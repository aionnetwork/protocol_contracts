package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import avm.Result;

import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.userlib.AionList;
import org.aion.avm.userlib.AionMap;
import org.aion.avm.userlib.AionSet;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A staker registry manages the staker registration, and provides an interface for coin-holders
 * to vote/unvote for a staker.
 */
public class StakerRegistry {

    // TODO: replace long with BigInteger once the ABI supports it.
    // TODO: add stake vs nAmp conversion.
    // TODO: implement MIN_STAKE to prevent attacker from trying multiple keys to get eligible for free.
    // TODO: replace hash graph with key-value storage for dynamic collections.

    public static final long STAKE_LOCK_UP_PERIOD = 6 * 60 * 24 * 7;
    public static final long ADDRESS_UPDATE_COOL_DOWN_PERIOD = 6 * 60 * 24 * 7;

    private static class Staker {
        private Address signingAddress;
        private Address coinbaseAddress;
        private long lastAddressUpdate;

        private BigInteger totalStake;

        // maps addresses to the stakes those addresses have sent to this staker
        // the sum of stakes.values() should always equal totalStake
        private Map<Address, BigInteger> stakes;

        private Set<Address> listeners;

        public Staker(Address signingAddress, Address coinbaseAddress, long lastAddressUpdate) {
            this.signingAddress = signingAddress;
            this.coinbaseAddress = coinbaseAddress;
            this.lastAddressUpdate = lastAddressUpdate;
            this.totalStake = BigInteger.ZERO;
            this.stakes = new AionMap<>();
            this.listeners = new AionSet<>();
        }
    }

    private static class LockedCoin {
        private BigInteger value;
        private long createdAt;

        public LockedCoin(BigInteger value, long createdAt) {
            this.value = value;
            this.createdAt = createdAt;
        }
    }

    private static Map<Address, Staker> stakers = new AionMap<>();
    private static Map<Address, Address> signingAddresses = new AionMap<>();
    private static Map<Address, List<LockedCoin>> lockedCoins = new AionMap<>();

    /**
     * Registers a staker. The caller address will be the identification
     * address of a registered staker.
     *
     * @param signingAddress  the address of key used for signing a PoS block
     * @param coinbaseAddress the address of key used for collecting block rewards
     */
    @Callable
    public static void registerStaker(Address signingAddress, Address coinbaseAddress) {
        Address caller = Blockchain.getCaller();

        requireNonNull(signingAddress);
        requireNonNull(coinbaseAddress);

        require(!signingAddresses.containsKey(signingAddress));
        require(!stakers.containsKey(caller));

        signingAddresses.put(signingAddress, caller);
        stakers.put(caller, new Staker(signingAddress, coinbaseAddress, Blockchain.getBlockNumber()));
    }

    /**
     * Votes for a staker. Any liquid coins, passed along the call, become locked stake.
     *
     * @param staker the address of the staker
     */
    @Callable
    public static void vote(Address staker) {
        Address caller = Blockchain.getCaller();
        BigInteger amount = Blockchain.getValue();

        requireStaker(staker);
        requirePositive(amount);

        Staker s = stakers.get(staker);
        s.totalStake = s.totalStake.add(amount);
        BigInteger previousStake = getOrDefault(s.stakes, caller, BigInteger.ZERO);
        putOrRemove(s.stakes, caller, previousStake.add(amount));
    }

    /**
     * Unvotes for a staker. After a successful unvote, the locked coins will be released
     * to the original owners, subject to lock-up period.
     *
     * @param staker the address of the staker
     * @param amount the amount of stake
     */
    @Callable
    public static void unvote(Address staker, long amount) {
        unvoteTo(staker, amount, Blockchain.getCaller());
    }

    /**
     * Un-votes for a staker, and receives the released fund using another account.
     *
     * @param staker   the address of the staker
     * @param amount   the amount of stake
     * @param receiver the receiving addresss
     */
    @Callable
    public static void unvoteTo(Address staker, long amount, Address receiver) {
        Address caller = Blockchain.getCaller();

        requireStaker(staker);
        requirePositive(amount);
        requireNonNull(receiver);

        Staker s = stakers.get(staker);
        BigInteger previousStake = getOrDefault(stakers.get(staker).stakes, caller, BigInteger.ZERO);
        BigInteger amountBI = BigInteger.valueOf(amount);

        require(amountBI.compareTo(previousStake) <= 0);

        s.totalStake = s.totalStake.subtract(amountBI);
        putOrRemove(s.stakes, caller, previousStake.subtract(amountBI));

        List<LockedCoin> coins = getOrDefault(lockedCoins, receiver, new AionList<>());
        coins.add(new LockedCoin(amountBI, Blockchain.getBlockNumber()));
        lockedCoins.put(receiver, coins);
    }

    /**
     * Transfers stake from one staker to another staker.
     *
     * @param fromStaker the address of the staker to transfer stake from
     * @param toStaker   the address of the staker to transfer stake to
     * @param amount     the amount of stake
     */
    @Callable
    public static void transferStake(Address fromStaker, Address toStaker, long amount) {
        Address caller = Blockchain.getCaller();

        requireStaker(fromStaker);
        requireStaker(toStaker);
        requirePositive(amount);

        Staker s1 = stakers.get(fromStaker);
        Staker s2 = stakers.get(toStaker);
        BigInteger previousStake1 = getOrDefault(s1.stakes, caller, BigInteger.ZERO);
        BigInteger previousStake2 = getOrDefault(s2.stakes, caller, BigInteger.ZERO);
        BigInteger amountBI = BigInteger.valueOf(amount);

        require(amountBI.compareTo(previousStake1) <= 0);

        s1.totalStake = s1.totalStake.subtract(amountBI);
        putOrRemove(s1.stakes, caller, previousStake1.subtract(amountBI));

        s2.totalStake = s2.totalStake.add(amountBI);
        putOrRemove(s2.stakes, caller, previousStake2.add(amountBI));
    }

    /**
     * Releases the stake (locked coin) to the owner.
     *
     * @param owner the owner address
     * @param limit the max number of limited coins to process
     * @return the number of locked coins released, not the amount
     */
    @Callable
    public static int releaseStake(Address owner, int limit) {
        requireNonNull(owner);
        requirePositive(limit);

        List<LockedCoin> coins = getOrDefault(lockedCoins, owner, new AionList<>());
        long blockNumber = Blockchain.getBlockNumber();

        int count = 0;
        for (int i = 0; i < coins.size() && i < limit; i++) {
            LockedCoin coin = coins.get(i);
            if (blockNumber >= coin.createdAt + STAKE_LOCK_UP_PERIOD) {
                secureCall(owner, coin.value, new byte[0], Blockchain.getRemainingEnergy());
                count++;
            } else {
                break;
            }
        }

        return count;
    }

    /**
     * Returns the total stake associated with a staker.
     *
     * @param staker the address of the staker
     * @return the total amount of stake
     */
    @Callable
    public static long getStakeByStakerAddress(Address staker) {
        requireStaker(staker);

        return stakers.get(staker).totalStake.longValue();
    }

    /**
     * Returns the total stake associated with a staker.
     *
     * @param staker the address of the staker
     * @return the total amount of stake
     */
    @Callable
    public static long getStakeBySigningAddress(Address staker) {
        return getStakeByStakerAddress(signingAddresses.get(staker));
    }

    /**
     * Returns the stake from a voter to a staker.
     *
     * @param staker the address of the staker
     * @param voter  the address of the staker
     * @return the amount of stake
     */
    @Callable
    public static long getStake(Address staker, Address voter) {
        requireStaker(staker);

        return getOrDefault(stakers.get(staker).stakes, voter, BigInteger.ZERO).longValue();
    }

    /**
     * Returns the signing address of a staker.
     *
     * @param staker the address of the staker.
     * @return the signing address
     */
    @Callable
    public static Address getSigningAddress(Address staker) {
        requireStaker(staker);

        return stakers.get(staker).signingAddress;
    }

    /**
     * Returns the coinbase address of a staker.
     *
     * @param staker the address of the staker.
     * @return the coinbase address
     */
    @Callable
    public static Address getCoinbaseAddress(Address staker) {
        requireStaker(staker);

        return stakers.get(staker).coinbaseAddress;
    }

    /**
     * Updates the signing address of a staker. Owner only.
     *
     * @param newSigningAddress the new signing address
     */
    @Callable
    public static void setSigningAddress(Address newSigningAddress) {
        Address caller = Blockchain.getCaller();
        requireStaker(caller);

        Staker s = stakers.get(caller);
        long blockNumber = Blockchain.getBlockNumber();

        require(blockNumber >= s.lastAddressUpdate + ADDRESS_UPDATE_COOL_DOWN_PERIOD);
        require(!signingAddresses.containsKey(newSigningAddress));

        signingAddresses.put(newSigningAddress, caller);
        s.signingAddress = newSigningAddress;
        s.lastAddressUpdate = blockNumber;

        for (Address listener : s.listeners) {
            byte[] data = new ABIStreamingEncoder()
                    .encodeOneString("onSigningAddressChange")
                    .encodeOneAddress(caller)
                    .encodeOneAddress(newSigningAddress)
                    .toBytes();
            secureCall(listener, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
        }
    }

    /**
     * Updates the coinbase address of a staker. Owner only.
     *
     * @param newCoinbaseAddress the new coinbase address
     */
    @Callable
    public static void setCoinbaseAddress(Address newCoinbaseAddress) {
        Address caller = Blockchain.getCaller();
        requireStaker(caller);

        Staker s = stakers.get(caller);
        long blockNumber = Blockchain.getBlockNumber();

        require(blockNumber >= s.lastAddressUpdate + ADDRESS_UPDATE_COOL_DOWN_PERIOD);

        s.coinbaseAddress = newCoinbaseAddress;

        for (Address listener : s.listeners) {
            byte[] data = new ABIStreamingEncoder()
                    .encodeOneString("onCoinbaseAddressChange")
                    .encodeOneAddress(caller)
                    .encodeOneAddress(newCoinbaseAddress)
                    .toBytes();
            secureCall(listener, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
        }
    }

    /**
     * Registers a listener. Owner only.
     *
     * @param listener the address of the listener contract
     */
    @Callable
    public static void registerListener(Address listener) {
        Address caller = Blockchain.getCaller();

        requireStaker(caller);

        stakers.get(caller).listeners.add(listener);
    }

    /**
     * Deregisters a listener. Owner only.
     *
     * @param listener the address of the listener contract
     */
    @Callable
    public static void deregisterListener(Address listener) {
        Address caller = Blockchain.getCaller();

        requireStaker(caller);

        stakers.get(caller).listeners.remove(listener);
    }

    private static void require(boolean condition) {
        // now implements as un-catchable
        Blockchain.require(condition);
    }

    private static void requireStaker(Address staker) {
        require(staker != null && stakers.containsKey(staker));
    }

    private static void requirePositive(BigInteger num) {
        require(num != null && num.compareTo(BigInteger.ZERO) > 0);
    }

    private static void requirePositive(long num) {
        require(num > 0);
    }

    private static void requireNonNull(Object obj) {
        require(obj != null);
    }

    private static <K, V extends BigInteger> void putOrRemove(Map<K, V> map, K key, V value) {
        if (value == null || value.compareTo(BigInteger.ZERO) == 0) {
            map.remove(key);
        } else {
            map.put(key, value);
        }
    }

    private static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultValue) {
        if (map.containsKey(key)) {
            return map.get(key);
        } else {
            return defaultValue;
        }
    }

    private static void secureCall(Address targetAddress, BigInteger value, byte[] data, long energyLimit) {
        Result result = Blockchain.call(targetAddress, value, data, energyLimit);
        require(result.isSuccess());
    }
}
