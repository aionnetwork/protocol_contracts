package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import avm.Result;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.userlib.AionMap;
import org.aion.avm.userlib.AionSet;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;

import java.beans.EventSetDescriptor;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

/**
 * A staker registry manages the staker database, and provides an interface for voters
 * to vote/unvote for a staker.
 */
public class StakerRegistry {

    // TODO: replace object graph-based collections with key-value storage.

    public static final long SIGNING_ADDRESS_COOLING_PERIOD = 6 * 60 * 24 * 7;
    public static final long UNVOTE_LOCK_UP_PERIOD = 6 * 60 * 24 * 7;
    public static final long TRANSFER_LOCK_UP_PERIOD = 6 * 10;

    public static final BigInteger MIN_SELF_STAKE = BigInteger.valueOf(1000L);

    private static class Staker {
        private Address identityAddress;
        private Address managementAddress;
        private Address signingAddress;
        private Address coinbaseAddress;
        private Address selfBondAddress;

        private long lastSigningAddressUpdate;

        private BigInteger totalStake;
        private boolean isActive;

        // maps addresses to the stakes those addresses have sent to this staker
        // the sum of stakes.values() should always equal totalStake
        private Map<Address, BigInteger> stakes;

        public Staker(Address identityAddress, Address managementAddress, Address signingAddress, Address coinbaseAddress, Address selfBondAddress, long lastSigningAddressUpdate) {
            this.identityAddress = identityAddress;
            this.managementAddress = managementAddress;
            this.signingAddress = signingAddress;
            this.coinbaseAddress = coinbaseAddress;
            this.selfBondAddress = selfBondAddress;
            this.lastSigningAddressUpdate = lastSigningAddressUpdate;
            this.totalStake = BigInteger.ZERO;
            this.stakes = new AionMap<>();
            this.isActive = true;
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
     * @param identityAddress  the identity of the staker; can't be changed
     * @param managementAddress  the address with management rights. can't be changed.
     * @param signingAddress  the address of the key used for signing PoS blocks
     * @param coinbaseAddress the address of the key used for collecting block rewards
     * @param selfBondAddress  the self bond is deposited by staker
     */
    @Callable
    public static void registerStaker(Address identityAddress, Address managementAddress,
                                      Address signingAddress, Address coinbaseAddress, Address selfBondAddress) {
        requireNonNull(identityAddress);
        requireNonNull(managementAddress);
        requireNonNull(signingAddress);
        requireNonNull(coinbaseAddress);
        requireNonNull(selfBondAddress);
        requireNoValue();

        require(!signingAddresses.containsKey(signingAddress));
        require(!stakers.containsKey(identityAddress));

        signingAddresses.put(signingAddress, identityAddress);

        stakers.put(identityAddress, new Staker(identityAddress, managementAddress, signingAddress, coinbaseAddress, selfBondAddress, Blockchain.getBlockNumber()));
        StakerRegistryEvents.registeredStaker(identityAddress, managementAddress, signingAddress, coinbaseAddress, selfBondAddress);
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
        StakerRegistryEvents.voted(caller, staker, amount);
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
        StakerRegistryEvents.unvoted(id, caller, staker, recipient, amountBI);

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
        StakerRegistryEvents.transferredStake(id, fromStaker, toStaker, recipient, amountBI);

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
        StakerRegistryEvents.finalizedUnvote(id);
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
        StakerRegistryEvents.finalizedTransfer(id);
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
     * Returns if staker is registered.
     *
     * @param staker the address of the staker
     * @return the amount of stake
     */
    @Callable
    public static boolean isStaker(Address staker) {
        return stakers.containsKey(staker);
    }

    /**
     * Returns whether a staker is active, subject to pre-defined rules, e.g. min_self_stake
     *
     * @param staker the address of staker
     * @return true if active, otherwise false
     */
    @Callable
    public static boolean isActive(Address staker) {
        requireNonNull(staker);
        requireNoValue();

        Staker s = stakers.get(staker);

        return s.isActive && BigInteger.valueOf(getStake(staker, s.selfBondAddress)).compareTo(MIN_SELF_STAKE) >= 0;
    }

    /**
     * Updates the active status of a staker. Owner only.
     *
     * @param isActive the new signing address
     */
    @Callable
    public static void setActive(Address staker, boolean isActive) {
        requireNoValue();

        Staker s =  requireStakerAndManager(staker, Blockchain.getCaller());
        if (isActive != s.isActive) {
            s.isActive = isActive;
        }
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
     * @param staker the identity address of the staker.
     * @return the coinbase address
     */
    @Callable
    public static Address getCoinbaseAddressForIdentityAddress(Address staker) {
        requireStaker(staker);
        requireNoValue();

        return stakers.get(staker).coinbaseAddress;
    }

    /**
     * Returns the coinbase address of a staker.
     *
     * @param signingAddress the signing address of the staker.
     * @return the coinbase address
     */
    @Callable
    public static Address getCoinbaseAddressForSigningAddress(Address signingAddress) {
        requireNoValue();

        Address identityAddress = signingAddresses.get(signingAddress);
        requireStaker(identityAddress);

        return stakers.get(identityAddress).coinbaseAddress;
    }

    // TODO: correct error checking.
    private static Staker requireStakerAndManager(Address staker, Address manager) {
        requireStaker(staker);
        Staker s = stakers.get(staker);
        require(s.managementAddress.equals(manager));

        return s;
    }
    /**
     * Updates the signing address of a staker. Owner only.
     *
     * @param newSigningAddress the new signing address
     */
    @Callable
    public static void setSigningAddress(Address staker, Address newSigningAddress) {
        requireNonNull(newSigningAddress);
        requireNoValue();

        Staker s =  requireStakerAndManager(staker, Blockchain.getCaller());
        if (!newSigningAddress.equals(s.signingAddress)) {
            // check last update
            long blockNumber = Blockchain.getBlockNumber();
            require(blockNumber >= s.lastSigningAddressUpdate + SIGNING_ADDRESS_COOLING_PERIOD);

            // check duplicated signing address
            require(!signingAddresses.containsKey(newSigningAddress));

            // the old signing address is removed and can be used again by another staker
            signingAddresses.remove(s.signingAddress);
            signingAddresses.put(newSigningAddress, s.identityAddress);
            s.signingAddress = newSigningAddress;
            s.lastSigningAddressUpdate = blockNumber;
        }
        // todo only if a new address?
        StakerRegistryEvents.setSigningAddress(staker, newSigningAddress);
    }

    /**
     * Updates the coinbase address of a staker. Owner only.
     *
     * @param newCoinbaseAddress the new coinbase address
     */
    @Callable
    public static void setCoinbaseAddress(Address staker, Address newCoinbaseAddress) {
        requireNonNull(newCoinbaseAddress);
        requireNoValue();

        Staker s = requireStakerAndManager(staker, Blockchain.getCaller());
        if (!newCoinbaseAddress.equals(s.coinbaseAddress)) {
            s.coinbaseAddress = newCoinbaseAddress;
        }
        StakerRegistryEvents.setCoinbaseAddress(staker, newCoinbaseAddress);
    }

    /**
     * Updates the coinbase address of a staker. Owner only.
     *
     * @param newAddress the new coinbase address
     */
    @Callable
    public static void setSelfBondAddress(Address staker, Address newAddress) {
        requireNonNull(newAddress);
        requireNoValue();

        Staker s = requireStakerAndManager(staker, Blockchain.getCaller());
        if (!newAddress.equals(s.selfBondAddress)) {
            s.selfBondAddress = newAddress;
        }
        StakerRegistryEvents.setSelfBondAddress(staker, newAddress);
    }

    @Callable
    public static long[] getPendingUnvoteIds() {
        requireNoValue();
        long[] pendingUnvoteIds = new long[pendingUnvotes.keySet().size()];
        int i = 0;
        for (long id : pendingUnvotes.keySet()) {
            pendingUnvoteIds[i++] = id;
        }
        return pendingUnvoteIds;
    }

    @Callable
    public static long[] getPendingTransferIds() {
        requireNoValue();
        long[] pendingTransferIds = new long[pendingTransfers.keySet().size()];
        int i = 0;
        for (long id : pendingTransfers.keySet()) {
            pendingTransferIds[i++] = id;
        }
        return pendingTransferIds;
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
