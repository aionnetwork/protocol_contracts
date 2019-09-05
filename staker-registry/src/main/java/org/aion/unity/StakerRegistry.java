package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import avm.Result;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.userlib.AionMap;

import java.math.BigInteger;
import java.util.Map;

/**
 * A staker registry manages the staker database, and provides an interface for delegators
 * to delegate/undelegate for a staker.
 */
public class StakerRegistry {

    // TODO: replace object graph-based collections with key-value storage.

    public static final long SIGNING_ADDRESS_COOLING_PERIOD = 6 * 60 * 24 * 7;
    public static final long UNDELEGATE_LOCK_UP_PERIOD = 6 * 60 * 24 * 7;
    public static final long TRANSFER_LOCK_UP_PERIOD = 6 * 10;

    // 1000 Aions
    public static final BigInteger MIN_SELF_STAKE = new BigInteger("1000000000000000000000");

    private static class Staker {
        private Address identityAddress;
        private Address managementAddress;
        private Address signingAddress;
        private Address coinbaseAddress;

        private long lastSigningAddressUpdate;

        private BigInteger totalStake;
        private BigInteger selfBondStake;

        // maps addresses to the stakes those addresses have sent to this staker
        // the sum of stakes.values() should always equal totalStake
        private Map<Address, BigInteger> stakes;

        public Staker(Address identityAddress, Address managementAddress, Address signingAddress, Address coinbaseAddress, long lastSigningAddressUpdate) {
            this.identityAddress = identityAddress;
            this.managementAddress = managementAddress;
            this.signingAddress = signingAddress;
            this.coinbaseAddress = coinbaseAddress;
            this.lastSigningAddressUpdate = lastSigningAddressUpdate;
            this.totalStake = BigInteger.ZERO;
            this.stakes = new AionMap<>();
            this.selfBondStake = BigInteger.ZERO;
        }
    }

    private static class PendingUndelegate {
        private Address recipient;
        private BigInteger value;
        private long blockNumber;

        public PendingUndelegate(Address recipient, BigInteger value, long blockNumber) {
            this.recipient = recipient;
            this.value = value;
            this.blockNumber = blockNumber;
        }
    }

    private static class PendingTransfer {
        private Address initiator;
        private Address toStaker;
        private Address recipient;
        private BigInteger value;
        private long blockNumber;

        public PendingTransfer(Address initiator, Address toStaker, Address recipient, BigInteger value, long blockNumber) {
            this.initiator = initiator;
            this.toStaker = toStaker;
            this.recipient = recipient;
            this.value = value;
            this.blockNumber = blockNumber;
        }
    }

    private static Map<Address, Staker> stakers = new AionMap<>();
    private static Map<Address, Address> signingAddresses = new AionMap<>();

    private static long nextUndelegateId = 0;
    private static Map<Long, PendingUndelegate> pendingUndelegates = new AionMap<>();
    private static long nextTransferId = 0;
    private static Map<Long, PendingTransfer> pendingTransfers = new AionMap<>();

    /**
     * Registers a staker. The caller address will be the identification
     * address of the new staker.
     *
     * @param identityAddress  the identity of the staker; can't be changed
     * @param managementAddress  the address with management rights. can't be changed.
     * @param signingAddress  the address of the key used for signing PoS blocks
     * @param coinbaseAddress the address of the key used for collecting block rewards
     */
    @Callable
    public static void registerStaker(Address identityAddress, Address managementAddress,
                                      Address signingAddress, Address coinbaseAddress) {
        requireNonNull(identityAddress);
        requireNonNull(managementAddress);
        requireNonNull(signingAddress);
        requireNonNull(coinbaseAddress);
        requireNoValue();

        require(!signingAddresses.containsKey(signingAddress));
        require(!stakers.containsKey(identityAddress));

        signingAddresses.put(signingAddress, identityAddress);

        stakers.put(identityAddress, new Staker(identityAddress, managementAddress, signingAddress, coinbaseAddress, Blockchain.getBlockNumber()));
        StakerRegistryEvents.registeredStaker(identityAddress, managementAddress, signingAddress, coinbaseAddress);
    }

    /**
     * Delegates to a staker. Any liquid coins, passed along the call, become locked stake.
     *
     * @param staker the address of the staker
     */
    @Callable
    public static void delegate(Address staker) {
        Address caller = Blockchain.getCaller();
        BigInteger amount = Blockchain.getValue();

        requireStaker(staker);
        requirePositive(amount);

        Staker s = stakers.get(staker);
        s.totalStake = s.totalStake.add(amount);
        BigInteger previousStake = getOrDefault(s.stakes, caller, BigInteger.ZERO);
        putOrRemove(s.stakes, caller, previousStake.add(amount));
        StakerRegistryEvents.delegated(caller, staker, amount);
    }

    /**
     * Un-delegates to a staker. After a successful undelegate, the locked coins will be released
     * to the original delegator, subject to lock-up period.
     *
     * @param staker the address of the staker
     * @param amount the amount of stake
     * @return a pending undelegation identifier
     */
    @Callable
    public static long undelegate(Address staker, BigInteger amount) {
        return undelegateTo(staker, amount, Blockchain.getCaller());
    }

    /**
     * Un-delegates for a staker, and receives the released fund using another account.
     *
     * @param staker   the address of the staker
     * @param amount   the amount of stake
     * @param recipient the receiving address
     * @return a pending un-delegation identifier
     */
    @Callable
    public static long undelegateTo(Address staker, BigInteger amount, Address recipient) {
        Address caller = Blockchain.getCaller();

        requireStaker(staker);
        requirePositive(amount);
        requireNonNull(recipient);
        requireNoValue();

        Staker s = stakers.get(staker);
        BigInteger previousStake = getOrDefault(stakers.get(staker).stakes, caller, BigInteger.ZERO);

        // check previous stake
        require(amount.compareTo(previousStake) <= 0);

        // update stake
        s.totalStake = s.totalStake.subtract(amount);
        putOrRemove(s.stakes, caller, previousStake.subtract(amount));

        // create pending un-delegate
        long id = nextUndelegateId++;
        PendingUndelegate undelegate = new PendingUndelegate(recipient, amount, Blockchain.getBlockNumber());
        pendingUndelegates.put(id, undelegate);
        StakerRegistryEvents.undelegated(id, caller, staker, recipient, amount);

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
    public static long transferDelegation(Address fromStaker, Address toStaker, BigInteger amount) {
        return transferDelegationTo(fromStaker, toStaker, amount, Blockchain.getCaller());
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
    public static long transferDelegationTo(Address fromStaker, Address toStaker, BigInteger amount, Address recipient) {
        Address caller = Blockchain.getCaller();

        requireStaker(fromStaker);
        requireStaker(toStaker);
        requirePositive(amount);
        require(!fromStaker.equals(toStaker));
        requireNoValue();

        Staker s = stakers.get(fromStaker);
        BigInteger previousStake = getOrDefault(s.stakes, caller, BigInteger.ZERO);

        // check previous stake
        require(amount.compareTo(previousStake) <= 0);

        // update stake
        s.totalStake = s.totalStake.subtract(amount);
        putOrRemove(s.stakes, caller, previousStake.subtract(amount));

        // create pending transfer
        long id = nextTransferId++;
        PendingTransfer transfer = new PendingTransfer(caller, toStaker, recipient, amount, Blockchain.getBlockNumber());
        pendingTransfers.put(id, transfer);
        StakerRegistryEvents.transferredDelegation(id, fromStaker, toStaker, recipient, amount);

        return id;
    }

    /**
     * Finalizes an undelegate operation, specified by id.
     *
     * @param id the pending un-delegate identifier
     */
    @Callable
    public static void finalizeUndelegate(long id) {
        requireNoValue();

        // check existence
        PendingUndelegate undelegate = pendingUndelegates.get(id);
        requireNonNull(undelegate);

        // lock-up period check
        require(Blockchain.getBlockNumber() >= undelegate.blockNumber + UNDELEGATE_LOCK_UP_PERIOD);

        // remove the undelegate
        pendingUndelegates.remove(id);

        // do a value transfer
        secureCall(undelegate.recipient, undelegate.value, new byte[0], Blockchain.getRemainingEnergy());
        StakerRegistryEvents.finalizedUndelegation(id);
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

        // remove the transfer
        pendingTransfers.remove(id);

        // credit the stake to the designated pool of the recipient
        Staker s = stakers.get(transfer.toStaker);
        BigInteger previousStake = getOrDefault(s.stakes, transfer.recipient, BigInteger.ZERO);
        s.totalStake = s.totalStake.add(transfer.value);
        putOrRemove(s.stakes, transfer.recipient, previousStake.add(transfer.value));
        StakerRegistryEvents.finalizedDelegationTransfer(id);
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
    public static BigInteger getEffectiveStake(Address signingAddress, Address coinbaseAddress) {
        requireNonNull(signingAddress);
        requireNoValue();

        // if not a staker
        Address staker = signingAddresses.get(signingAddress);
        if (staker == null) {
            return BigInteger.ZERO;
        }

        // if coinbase addresses do not match
        if (!stakers.get(staker).coinbaseAddress.equals(coinbaseAddress)) {
            return BigInteger.ZERO;
        }

        // if not active
        if (!isActive(staker)) {
            return BigInteger.ZERO;
        }

        // query total stake
        BigInteger totalStake = getTotalStake(staker);

        // conversion: 1 nAmp = 1 stake
        return totalStake;
    }

    /**
     * Returns the total stake of a staker.
     *
     * @param staker the address of the staker
     * @return the total amount of stake
     */
    @Callable
    public static BigInteger getTotalStake(Address staker) {
        requireStaker(staker);
        requireNoValue();

        return stakers.get(staker).totalStake;
    }


    /**
     * Returns the stake from a delegator to a staker.
     *
     * @param staker the address of the staker
     * @param delegator  the address of the delegator
     * @return the amount of stake
     */
    @Callable
    public static BigInteger getStake(Address staker, Address delegator) {
        requireStaker(staker);
        requireNonNull(delegator);
        requireNoValue();

        return getOrDefault(stakers.get(staker).stakes, delegator, BigInteger.ZERO);
    }

    /**
     * Bonds the stake to the staker (self-bond stake)
     * @param staker the address of the staker
     */
    @Callable
    public static void bond(Address staker){
        BigInteger amount = Blockchain.getValue();

        requirePositive(amount);
        requireStakerAndManager(staker, Blockchain.getCaller());

        Staker s = stakers.get(staker);
        s.selfBondStake = s.selfBondStake.add(amount);
        s.totalStake = s.totalStake.add(amount);

        StakerRegistryEvents.bonded(staker, amount);
    }

    /**
     * Unbonds for a staker, After a successful unbond, the locked coins will be released to the original bonder (management address).
     * This is subject to lock-up period.
     * @param staker the address of the staker
     * @param amount the amount of stake
     * @return a pending un-delegate identifier
     */
    @Callable
    public static long unbond(Address staker, BigInteger amount){
        return unbondTo(staker, amount, Blockchain.getCaller());
    }

    /**
     * Unbonds for a staker, After a successful unbond, the locked coins will be released to the specified account.
     * This is subject to lock-up period.
     * @param staker the address of the staker
     * @param amount the amount of stake
     * @param recipient the receiving address
     * @return a pending un-delegate identifier
     */
    @Callable
    public static long unbondTo(Address staker, BigInteger amount, Address recipient){
        Address caller = Blockchain.getCaller();

        requireStakerAndManager(staker, caller);
        requirePositive(amount);
        requireNoValue();

        Staker s = stakers.get(staker);

        require(amount.compareTo(s.selfBondStake) <= 0);

        s.selfBondStake = s.selfBondStake.subtract(amount);
        s.totalStake = s.totalStake.subtract(amount);

        long id = nextUndelegateId++;
        PendingUndelegate undelegate = new PendingUndelegate(recipient, amount, Blockchain.getBlockNumber());
        pendingUndelegates.put(id, undelegate);

        StakerRegistryEvents.unbonded(id, staker, recipient, amount);

        return id;
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

        return s.selfBondStake.compareTo(MIN_SELF_STAKE) >= 0;
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
            StakerRegistryEvents.setSigningAddress(staker, newSigningAddress);
        }
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
            StakerRegistryEvents.setCoinbaseAddress(staker, newCoinbaseAddress);
        }
    }

    @Callable
    public static long[] getPendingUndelegateIds() {
        requireNoValue();
        long[] pendingUndelegateIds = new long[pendingUndelegates.keySet().size()];
        int i = 0;
        for (long id : pendingUndelegates.keySet()) {
            pendingUndelegateIds[i++] = id;
        }
        return pendingUndelegateIds;
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

    @Callable
    public static BigInteger getSelfBondStake(Address staker) {
        requireStaker(staker);
        requireNoValue();
        return stakers.get(staker).selfBondStake;
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
