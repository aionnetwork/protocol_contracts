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
 * A staker registry manages the staker database, and provides an interface for voters
 * to vote/unvote for a staker.
 */
public class StakerRegistry {

    // TODO: replace long with BigInteger once the ABI supports it.
    // TODO: replace object graph-based collections with key-value storage.
    // TODO: add events.

    public static final long SIGNING_ADDRESS_COOLING_PERIOD = 6 * 60 * 24 * 7;
    public static final long UNVOTE_LOCK_UP_PERIOD = 6 * 60 * 24 * 7;
    public static final long TRANSFER_LOCK_UP_PERIOD = 6 * 10;

    public static final BigInteger MIN_SELF_STAKE = BigInteger.valueOf(1000L);
    public static final BigInteger PENALTY_AMOUNT = BigInteger.valueOf(100L);

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
        private Address initiator;
        private Address recipient;
        private BigInteger value;
        private long blockNumber;

        public PendingUnvote(Address initiator, Address recipient, BigInteger value, long blockNumber) {
            this.initiator = initiator;
            this.recipient = recipient;
            this.value = value;
            this.blockNumber = blockNumber;
        }
    }

    private static class PendingTransfer {
        private Address initiator;
        private Address fromStaker;
        private Address toStaker;
        private Address recipient;
        private BigInteger value;
        private long blockNumber;

        public PendingTransfer(Address initiator, Address fromStaker, Address toStaker, Address recipient, BigInteger value, long blockNumber) {
            this.initiator = initiator;
            this.fromStaker = fromStaker;
            this.toStaker = toStaker;
            this.recipient = recipient;
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
     * address of the new staker.
     *
     * @param signingAddress  the address of the key used for signing PoS blocks
     * @param coinbaseAddress the address of the key used for collecting block rewards
     */
    @Callable
    public static void registerStaker(Address signingAddress, Address coinbaseAddress) {
        Address caller = Blockchain.getCaller();

        requireNonNull(signingAddress);
        requireNonNull(coinbaseAddress);
        requireNoValue();

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
     * @param recipient the receiving addresss
     * @return a pending unvote identifier
     */
    @Callable
    public static long unvoteTo(Address staker, long amount, Address recipient) {
        Address caller = Blockchain.getCaller();

        requireStaker(staker);
        requirePositive(amount);
        requireNonNull(recipient);
        requireNoValue();

        Staker s = stakers.get(staker);
        BigInteger previousStake = getOrDefault(stakers.get(staker).stakes, caller, BigInteger.ZERO);
        BigInteger amountBI = BigInteger.valueOf(amount);

        // check previous stake
        require(amountBI.compareTo(previousStake) <= 0);

        // update stake
        s.totalStake = s.totalStake.subtract(amountBI);
        putOrRemove(s.stakes, caller, previousStake.subtract(amountBI));

        // create pending unvote
        long id = nextUnvote++;
        PendingUnvote unvote = new PendingUnvote(caller, recipient, BigInteger.valueOf(amount), Blockchain.getBlockNumber());
        pendingUnvotes.put(id, unvote);

        return id;
    }

    /**
     * Transfers stake from one staker to another staker.
     *
     * @param fromStaker the address of the staker to transfer stake from
     * @param toStaker   the address of the staker to transfer stake to
     * @param amount     the amount of stake
     * @return a pending transfer identifier
     */
    @Callable
    public static long transferStake(Address fromStaker, Address toStaker, long amount) {
        return transferStakeTo(fromStaker, toStaker, amount, Blockchain.getCaller());
    }

    /**
     * Transfers stake from one staker to another staker, and designates a new owner of the stake.
     *
     * @param fromStaker the address of the staker to transfer stake from
     * @param toStaker   the address of the staker to transfer stake to
     * @param amount     the amount of stake
     * @param recipient  the new owner of the stake being transferred
     * @return a pending transfer identifier
     */
    @Callable
    public static long transferStakeTo(Address fromStaker, Address toStaker, long amount, Address recipient) {
        Address caller = Blockchain.getCaller();

        requireStaker(fromStaker);
        requireStaker(toStaker);
        requirePositive(amount);
        require(!fromStaker.equals(toStaker));
        requireNoValue();

        Staker s = stakers.get(fromStaker);
        BigInteger previousStake = getOrDefault(s.stakes, caller, BigInteger.ZERO);
        BigInteger amountBI = BigInteger.valueOf(amount);

        // check previous stake
        require(amountBI.compareTo(previousStake) <= 0);

        // update stake
        s.totalStake = s.totalStake.subtract(amountBI);
        putOrRemove(s.stakes, caller, previousStake.subtract(amountBI));

        // create pending transfer
        long id = nextTransfer++;
        PendingTransfer transfer = new PendingTransfer(caller, fromStaker, toStaker, recipient, BigInteger.valueOf(amount), Blockchain.getBlockNumber());
        pendingTransfers.put(id, transfer);

        return id;
    }

    /**
     * Finalizes an un-vote operation, specified by id.
     *
     * @param id the pending unvote identifier
     */
    @Callable
    public static void finalizeUnvote(long id) {
        requireNoValue();

        // check existence
        PendingUnvote unvote = pendingUnvotes.get(id);
        requireNonNull(unvote);

        // lock-up period check
        require(Blockchain.getBlockNumber() >= unvote.blockNumber + UNVOTE_LOCK_UP_PERIOD);

        // remove the unvote
        pendingUnvotes.remove(id);

        // do a value transfer
        secureCall(unvote.recipient, unvote.value, new byte[0], Blockchain.getRemainingEnergy());
    }

    /**
     * Finalizes a transfer operations.
     *
     * @param id pending transfer identifier
     */
    @Callable
    public static void finalizeTransfer(long id) {
        requireNoValue();

        // check existence
        PendingTransfer transfer = pendingTransfers.get(id);
        requireNonNull(transfer);

        // only the initiator can finalize the transfer, mainly because
        // the pool registry needs to keep track of stake transfers.
        // TODO: a more elegant way would be using listener?
        require(Blockchain.getCaller().equals(transfer.initiator));

        // lock-up period check
        require(Blockchain.getBlockNumber() >= transfer.blockNumber + TRANSFER_LOCK_UP_PERIOD);

        // remvoe the transfer
        pendingTransfers.remove(id);

        // credit the stake to the designated pool of the recipient
        Staker s = stakers.get(transfer.toStaker);
        BigInteger previousStake = getOrDefault(s.stakes, transfer.recipient, BigInteger.ZERO);
        s.totalStake = s.totalStake.add(transfer.value);
        putOrRemove(s.stakes, transfer.recipient, previousStake.add(transfer.value));
    }

    private static Set<ByteArrayWrapper> slashedHeaders = new AionSet<>();

    @Callable
    public static void slash(int type, byte[] ...headers) {
        switch(type) {
            // Here, I'm implementing one slashing condition, dunkle, to test
            // the slashing workflow. Please replace with whatever conditions
            // the team eventually decides.
            case 1:
                require(headers.length == 1);

                // decode block header
                AionBlockHeader header = new AionBlockHeader(headers[0]);
                byte[] hash = header.getHash();
                byte[] correctHash = "test".getBytes(); // FIXME: use `Blockchain.getBlockHash(header.number)`;

                // avoid double-slashing
                require(!slashedHeaders.contains(new ByteArrayWrapper(hash)));

                if (!Arrays.equals(hash, correctHash)) {
                    // find the staker
                    Address staker = stakers.keySet().iterator().next(); // FIXME: use `signingAddresses.get(header.getSigner())`;
                    requireNonNull(staker);

                    slash(staker);
                }
                break;
        }
    }

    private static void slash(Address staker) {
        Staker s = stakers.get(staker);

        // deduct the stake of the staker
        BigInteger selfStake = getOrDefault(s.stakes, staker, BigInteger.ZERO);
        require(selfStake.compareTo(PENALTY_AMOUNT) >= 0);
        s.stakes.put(staker, selfStake.min(PENALTY_AMOUNT));

        // transfer the slashed stake to the reporter TODO: Yao has different view on this
        secureCall(Blockchain.getCaller(), PENALTY_AMOUNT, new byte[0], Blockchain.getRemainingEnergy());

        // NOTE: when calling the onSlash callback, cap the energy limit and ignore
        // the call results. Otherwise, a staker can set up a energy sucker as listener
        // to prevent a slashing.

        // Also, to make sure the pool registry have enough energy to execute
        // its internal logic, we should check the remaining energy here.

        // In conclusion, we need to make sure the listener get executed with enough
        // energy and prevent a listener from stopping a slashing event.

        // FIXME: reduce this number after optimizing the energy usage in pool registry.
        long energyForListener = 2_000_000L;
        long remainingEnergy = Blockchain.getRemainingEnergy();
        require(remainingEnergy > energyForListener * s.listeners.size() + 100_000L);

        // FIXME: limit the max number of listeners per staker
        // otherwise, staker can register many listeners to prevent slashing.

        for (Address listener : s.listeners) {
            byte[] data = new ABIStreamingEncoder()
                    .encodeOneString("onSlashing")
                    .encodeOneAddress(staker)
                    .encodeOneLong(PENALTY_AMOUNT.longValue())
                    .toBytes();
            Blockchain.call(listener, BigInteger.ZERO, data, energyForListener);
        }
    }


    /**
     * Returns the effective stake, after conversion and status check, of a staker.
     *
     * Designed for kernel usage only.
     *
     * @param signingAddress the signing address extracted from block header
     * @param coinbaseAddress the coinbase address extracted from block header
     * @return the effective stake of the staker
     */
    @Callable
    public static long getEffectiveStake(Address signingAddress, Address coinbaseAddress) {
        requireNonNull(signingAddress);
        requireNoValue();

        // if not a staker
        Address staker = signingAddresses.get(signingAddress);
        if (staker == null) {
            return 0;
        }

        // if coinbase addresses do not match
        if (!stakers.get(staker).coinbaseAddress.equals(coinbaseAddress)) {
            return 0;
        }

        // if not active
        if (!isActive(staker)) {
            return 0;
        }

        // query total stake
        long totalStake = getTotalStake(staker);

        // FIXME: define the conversion, presumably 1 AION = 1 stake
        long effectiveStake = totalStake / 1;

        return effectiveStake;
    }

    /**
     * Returns the total stake of a staker.
     *
     * @param staker the address of the staker
     * @return the total amount of stake
     */
    @Callable
    public static long getTotalStake(Address staker) {
        requireStaker(staker);
        requireNoValue();

        return stakers.get(staker).totalStake.longValue();
    }

    /**
     * Returns the self-bond stake of a staker
     *
     * @param staker the address of the staker
     * @return the amount of self-bond stake
     */
    @Callable
    public static long getSelfStake(Address staker) {
        return getStake(staker, staker);
    }

    /**
     * Returns the stake from a voter to a staker.
     *
     * @param staker the address of the staker
     * @param voter  the address of the voter
     * @return the amount of stake
     */
    @Callable
    public static long getStake(Address staker, Address voter) {
        requireStaker(staker);
        requireNonNull(voter);
        requireNoValue();

        return getOrDefault(stakers.get(staker).stakes, voter, BigInteger.ZERO).longValue();
    }

    /**
     * Returns whether a staker is active, subject to pre-defined rules, e.g. min_self_stake
     * and slashing rules.
     *
     * @param staker the address of staker
     * @return true if active, otherwise false
     */
    @Callable
    public static boolean isActive(Address staker) {
        requireNonNull(staker);
        requireNoValue();

        return BigInteger.valueOf(getStake(staker, staker)).compareTo(MIN_SELF_STAKE) >= 0;
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
        requireNoValue();

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
        requireNoValue();

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
        requireNoValue();

        Staker s = stakers.get(caller);
        if (!newSigningAddress.equals(s.signingAddress)) {
            // check last update
            long blockNumber = Blockchain.getBlockNumber();
            require(blockNumber >= s.lastSigningAddressUpdate + SIGNING_ADDRESS_COOLING_PERIOD);

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
        requireNoValue();

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
     * Registers a listener to a staker. Owner only.
     *
     * @param listener the address of the listener
     */
    @Callable
    public static void addListener(Address listener) {
        Address caller = Blockchain.getCaller();
        requireStaker(caller);
        requireNoValue();

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
     * Removes a listener of a staker. Owner only.
     *
     * @param listener the address of the listener contract
     */
    @Callable
    public static void removeListener(Address listener) {
        Address caller = Blockchain.getCaller();

        requireStaker(caller);
        requireNoValue();

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
        requireNoValue();

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

    private static void requireNoValue() {
        require(Blockchain.getValue().equals(BigInteger.ZERO));
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
