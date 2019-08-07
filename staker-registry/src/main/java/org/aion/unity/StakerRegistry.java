package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import avm.Result;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.userlib.AionMap;
import org.aion.avm.userlib.AionSet;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

/**
 * A staker registry manages the staker registration, and provides an interface for voters
 * to vote/unvote for a staker.
 */
public class StakerRegistry {

    // TODO: replace long with BigInteger once the ABI supports it.
    // TODO: add stake vs nAmp conversion, presumably 1 AION = 1 STAKE.
    // TODO: implement MIN_STAKE to prevent attacker from trying multiple keys to get eligible for free.
    // TODO: replace object graph-based collections with key-value storage.

    public static final long SIGNING_ADDRESS_COOL_DOWN_PERIOD = 6 * 60 * 24 * 7;
    public static final long UNVOTE_LOCK_UP_PERIOD = 6 * 60 * 24 * 7;
    public static final long TRANSFER_LOCK_UP_PERIOD = 6 * 10;

    private static class Staker {
        private Address signingAddress;
        private Address coinbaseAddress;

        private long lastSigningAddressUpdate;

        private BigInteger totalStake;

        // maps addresses to the stakes those addresses have sent to this staker
        // the sum of stakes.values() should always equal totalStake
        private Map<Address, BigInteger> stakes;

        private Set<Address> listeners;

        public Staker(Address signingAddress, Address coinbaseAddress, long lastSigningAddressUpdate) {
            this.signingAddress = signingAddress;
            this.coinbaseAddress = coinbaseAddress;
            this.lastSigningAddressUpdate = lastSigningAddressUpdate;
            this.totalStake = BigInteger.ZERO;
            this.stakes = new AionMap<>();
            this.listeners = new AionSet<>();
        }
    }

    private static class PendingUnvote {
        private Address to;
        private BigInteger value;
        private long blockNumber;

        public PendingUnvote(Address to, BigInteger value, long blockNumber) {
            this.to = to;
            this.value = value;
            this.blockNumber = blockNumber;
        }
    }

    private static class PendingTransfer {
        private Address from;
        private Address to;
        private BigInteger value;
        private long blockNumber;

        public PendingTransfer(Address from, Address to, BigInteger value, long blockNumber) {
            this.from = from;
            this.to = to;
            this.value = value;
            this.blockNumber = blockNumber;
        }
    }

    private static Map<Address, Staker> stakers = new AionMap<>();
    private static Map<Address, Address> signingAddresses = new AionMap<>();

    private static long nextUnvote = 0;
    private static Map<Long, PendingUnvote> pendingUnvotes = new AionMap<>();
    private static long nextTransfer = 0;
    private static Map<Long, PendingTransfer> pendingTransfers = new AionMap<>();

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
     * to the original voters, subject to lock-up period.
     *
     * @param staker the address of the staker
     * @param amount the amount of stake
     * @return a pending unvote identity
     */
    @Callable
    public static long unvote(Address staker, long amount) {
        return unvoteTo(staker, amount, Blockchain.getCaller());
    }

    /**
     * Un-votes for a staker, and receives the released fund using another account.
     *
     * @param staker   the address of the staker
     * @param amount   the amount of stake
     * @param receiver the receiving addresss
     * @return a pending unvote identifier
     */
    @Callable
    public static long unvoteTo(Address staker, long amount, Address receiver) {
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

        long id = nextUnvote++;
        PendingUnvote unvote = new PendingUnvote(receiver, BigInteger.valueOf(amount), Blockchain.getBlockNumber());
        pendingUnvotes.put(id, unvote);

        return id;
    }

    /**
     * Transfers stake from one staker to another staker.
     * <p>
     * TODO: attack vector - attacker may move their stake between accounts to maximize the profits
     *
     * @param fromStaker the address of the staker to transfer stake from
     * @param toStaker   the address of the staker to transfer stake to
     * @param amount     the amount of stake
     * @return a pending transfer identifier
     */
    @Callable
    public static long transferStake(Address fromStaker, Address toStaker, long amount) {
        Address caller = Blockchain.getCaller();

        requireStaker(fromStaker);
        requireStaker(toStaker);
        requirePositive(amount);

        Staker s = stakers.get(fromStaker);
        BigInteger previousStake = getOrDefault(s.stakes, caller, BigInteger.ZERO);
        BigInteger amountBI = BigInteger.valueOf(amount);

        require(amountBI.compareTo(previousStake) <= 0);

        s.totalStake = s.totalStake.subtract(amountBI);
        putOrRemove(s.stakes, caller, previousStake.subtract(amountBI));

        long id = nextTransfer++;
        PendingTransfer transfer = new PendingTransfer(caller, toStaker, BigInteger.valueOf(amount), Blockchain.getBlockNumber());
        pendingTransfers.put(id, transfer);

        return id;
    }

    /**
     * Finalizes an un-vote operations.
     *
     * @param id pending unvote identifier
     */
    @Callable
    public static void finalizeUnvote(long id) {
        PendingUnvote unvote = pendingUnvotes.get(id);
        requireNonNull(unvote);

        // lock-up period check
        require(Blockchain.getBlockNumber() >= unvote.blockNumber + UNVOTE_LOCK_UP_PERIOD);

        pendingUnvotes.remove(id);

        // do a transfer
        secureCall(unvote.to, unvote.value, new byte[0], Blockchain.getRemainingEnergy());
    }

    /**
     * Finalizes a transfer operations.
     *
     * @param id pending transfer identifier
     */
    @Callable
    public static void finalizeTransfer(long id) {
        PendingTransfer transfer = pendingTransfers.get(id);
        requireNonNull(transfer);

        // only the sender can finalize the transfer, mainly because
        // the pool registry needs to keep track of this.
        // more consideration is required.
        require(Blockchain.getCaller().equals(transfer.from));

        // lock-up period check
        require(Blockchain.getBlockNumber() >= transfer.blockNumber + TRANSFER_LOCK_UP_PERIOD);

        pendingTransfers.remove(id);

        Staker s = stakers.get(transfer.to);
        BigInteger previousStake = getOrDefault(s.stakes, transfer.from, BigInteger.ZERO);
        s.totalStake = s.totalStake.add(transfer.value);
        putOrRemove(s.stakes, transfer.from, previousStake.add(transfer.value));
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
        requireNonNull(voter);

        return getOrDefault(stakers.get(staker).stakes, voter, BigInteger.ZERO).longValue();
    }

    /**
     * Returns the stake from the staker itself.
     *
     * @param staker the address of staker
     * @return the amount of stake
     */
    public static long getSelfStake(Address staker) {
        return getStake(staker, staker);
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
        requireNonNull(newSigningAddress);

        Staker s = stakers.get(caller);
        if (!newSigningAddress.equals(s.signingAddress)) {
            // check last update
            long blockNumber = Blockchain.getBlockNumber();
            require(blockNumber >= s.lastSigningAddressUpdate + SIGNING_ADDRESS_COOL_DOWN_PERIOD);

            // check duplicated signing address
            require(!signingAddresses.containsKey(newSigningAddress));

            signingAddresses.put(newSigningAddress, caller);
            s.signingAddress = newSigningAddress;
            s.lastSigningAddressUpdate = blockNumber;

            for (Address listener : s.listeners) {
                byte[] data = new ABIStreamingEncoder()
                        .encodeOneString("onSigningAddressChange")
                        .encodeOneAddress(caller)
                        .encodeOneAddress(newSigningAddress)
                        .toBytes();
                secureCall(listener, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
            }
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
        requireNonNull(newCoinbaseAddress);

        Staker s = stakers.get(caller);
        if (!newCoinbaseAddress.equals(s.coinbaseAddress)) {
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
    }

    /**
     * Registers a listener. Owner only.
     *
     * @param listener the address of the listener contract
     */
    @Callable
    public static void addListener(Address listener) {
        Address caller = Blockchain.getCaller();
        requireStaker(caller);

        Staker s = stakers.get(caller);
        if (!s.listeners.contains(listener)) {
            s.listeners.add(listener);

            // notify the listener
            byte[] data = new ABIStreamingEncoder()
                    .encodeOneString("onListenerAdded")
                    .encodeOneAddress(caller)
                    .toBytes();
            secureCall(listener, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
        }
    }

    /**
     * Deregisters a listener. Owner only.
     *
     * @param listener the address of the listener contract
     */
    @Callable
    public static void removeListener(Address listener) {
        Address caller = Blockchain.getCaller();
        requireStaker(caller);

        Staker s = stakers.get(caller);
        if (s.listeners.contains(listener)) {
            s.listeners.remove(listener);

            // notify the listener
            byte[] data = new ABIStreamingEncoder()
                    .encodeOneString("onListenerRemoved")
                    .encodeOneAddress(caller)
                    .toBytes();
            secureCall(listener, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
        }
    }

    /**
     * Returns if the given listener is registered to the staker.
     *
     * @param staker   the staker address
     * @param listener the listener address
     */
    @Callable
    public static boolean isListener(Address staker, Address listener) {
        requireStaker(staker);
        requireNonNull(listener);

        return stakers.get(staker).listeners.contains(listener);
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
