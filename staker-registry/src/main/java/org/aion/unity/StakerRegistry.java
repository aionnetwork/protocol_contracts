package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import avm.Result;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.tooling.abi.Fallback;

import java.math.BigInteger;

/**
 * A staker registry manages the staker database.
 */
public class StakerRegistry {

    public static final long SIGNING_ADDRESS_COOLING_PERIOD = 6 * 60 * 24 * 7;
    public static final long UNDELEGATE_LOCK_UP_PERIOD = 6 * 60 * 24 * 7;
    public static final long TRANSFER_LOCK_UP_PERIOD = 6 * 10;

    // 1000 Aions
    public static final BigInteger MIN_SELF_STAKE = new BigInteger("1000000000000000000000");

    private static long nextUndelegateId = 0;
    private static long nextTransferId = 0;

    /**
     * Registers a staker. The caller address will be the identification
     * address of the new staker.
     * Note that the minimum self bond value should be passed along the call.
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

        require(StakerRegistryStorage.getIdentityAddress(signingAddress) == null);
        require(StakerRegistryStorage.getStakerStakeInfo(identityAddress) == null);

        BigInteger selfStake = Blockchain.getValue();
        require(selfStake.compareTo(MIN_SELF_STAKE) >= 0);

        // signingAddress -> identityAddress
        StakerRegistryStorage.putIdentityAddress(signingAddress, identityAddress);

        StakerRegistryStorage.putManagementAddress(identityAddress, managementAddress);
        StakerRegistryStorage.putStakerAddressInfo(identityAddress, new StakerStorageObjects.AddressInfo(signingAddress, coinbaseAddress, Blockchain.getBlockNumber()));

        StakerStorageObjects.StakeInfo stakeInfo = new StakerStorageObjects.StakeInfo(BigInteger.ZERO, selfStake);

        StakerRegistryStorage.putStakerStakeInfo(identityAddress, stakeInfo);
        // default state for new stakers is set as true. This can only be explicitly changed by the management address.
        // ability to produce blocks depends on both the state and the self bond requirement.
        StakerRegistryStorage.putState(identityAddress, true);

        StakerRegistryEvents.registeredStaker(identityAddress, managementAddress, signingAddress, coinbaseAddress);
    }

    /**
     * Delegates to a staker. Any liquid coins, passed along the call, become locked stake.
     * This can only be called by the management address of the staker and it's mainly used to support pools.
     * Note that the delegated amount will not be counted towards the self bond value.
     *
     * @param staker the address of the staker
     */
    @Callable
    public static void delegate(Address staker) {
        requireStakerAndManager(staker, Blockchain.getCaller());

        BigInteger amount = Blockchain.getValue();
        requirePositive(amount);

        StakerStorageObjects.StakeInfo stakeInfo = StakerRegistryStorage.getStakerStakeInfo(staker);

        stakeInfo.otherStake = stakeInfo.otherStake.add(amount);
        StakerRegistryStorage.putStakerStakeInfo(staker, stakeInfo);

        StakerRegistryEvents.delegated(staker, amount);
    }

    /**
     * Un-delegates from a staker. After a successful undelegate, the locked coins will be released
     * to the original delegator, subject to lock-up period.
     *
     * @param staker the address of the staker
     * @param amount the amount of stake to undelegate. Self bond stake cannot be undelegated
     * @param fee the amount of stake that will be transferred to the account that invokes finalizeUndelegate
     * @return a pending undelegation identifier
     */
    @Callable
    public static long undelegate(Address staker, BigInteger amount, BigInteger fee) {
        return undelegateTo(staker, amount, Blockchain.getCaller(), fee);
    }

    /**
     * Un-delegates from a staker, and receives the released fund using another account.
     *
     * @param staker   the address of the staker
     * @param amount   the amount of stake to undelegate. Self bond stake cannot be undelegated
     * @param recipient the receiving address
     * @param fee the amount of stake that will be transferred to the account that invokes finalizeUndelegate
     * @return a pending un-delegation identifier
     */
    @Callable
    public static long undelegateTo(Address staker, BigInteger amount, Address recipient, BigInteger fee) {
        requireStakerAndManager(staker, Blockchain.getCaller());

        requirePositive(amount);
        requireNonNull(recipient);
        requireNoValue();
        require(fee.signum() >= 0 && fee.compareTo(amount) <= 0);

        StakerStorageObjects.StakeInfo stakeInfo = StakerRegistryStorage.getStakerStakeInfo(staker);

        // check previous stake
        require(amount.compareTo(stakeInfo.otherStake) <= 0);

        // update stake
        stakeInfo.otherStake = stakeInfo.otherStake.subtract(amount);
        StakerRegistryStorage.putStakerStakeInfo(staker, stakeInfo);

        // create pending un-delegate
        long id = nextUndelegateId++;
        StakerStorageObjects.PendingUndelegate undelegate = new StakerStorageObjects.PendingUndelegate(recipient, amount, fee, Blockchain.getBlockNumber());
        StakerRegistryStorage.putPendingUndelegte(id, undelegate);
        StakerRegistryEvents.undelegated(id, staker, recipient, amount, fee);

        return id;
    }

    /**
     * Transfers stake from one staker to another staker.
     * Note that fromStaker's self bond stake cannot be transferred and the transfer value will not be counted towards toStaker's self bond.
     *
     * @param fromStaker the address of the staker to transfer stake from
     * @param toStaker   the address of the staker to transfer stake to
     * @param amount     the amount of stake
     * @param fee the amount of stake that will be transferred to the account that invokes finalizeTransfer
     * @return a pending transfer identifier
     */
    @Callable
    public static long transferDelegation(Address fromStaker, Address toStaker, BigInteger amount, BigInteger fee) {
        Address caller = Blockchain.getCaller();
        requireStakerAndManager(fromStaker, caller);

        StakerStorageObjects.StakeInfo stakeInfo = StakerRegistryStorage.getStakerStakeInfo(fromStaker);
        validateAndGetStakeInfo(toStaker);
        requirePositive(amount);
        require(!fromStaker.equals(toStaker));
        requireNoValue();
        // fee should be less than the amount for the delegate to be successful and not revert
        require(fee.signum() >= 0 && fee.compareTo(amount) < 0);

        // check previous stake
        require(amount.compareTo(stakeInfo.otherStake) <= 0);

        // update stake
        stakeInfo.otherStake = stakeInfo.otherStake.subtract(amount);
        StakerRegistryStorage.putStakerStakeInfo(fromStaker, stakeInfo);

        // create pending transfer
        long id = nextTransferId++;
        StakerStorageObjects.PendingTransfer transfer = new StakerStorageObjects.PendingTransfer(caller, toStaker, amount, fee, Blockchain.getBlockNumber());
        StakerRegistryStorage.putPendingTransfer(id, transfer);
        StakerRegistryEvents.transferredDelegation(id, fromStaker, toStaker, amount, fee);

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
        StakerStorageObjects.PendingUndelegate undelegate = StakerRegistryStorage.getPendingUndelegate(id);
        requireNonNull(undelegate);

        // lock-up period check
        require(Blockchain.getBlockNumber() >= undelegate.blockNumber + UNDELEGATE_LOCK_UP_PERIOD);

        // remove the undelegate
        StakerRegistryStorage.putPendingUndelegte(id, null);

        BigInteger remainingStake = undelegate.value.subtract(undelegate.fee);

        // transfer (stake - fee) to the undelegate recipient
        secureCall(undelegate.recipient, remainingStake, new byte[0], Blockchain.getRemainingEnergy());
        // transfer undelegate fee to the caller
        secureCall(Blockchain.getCaller(), undelegate.fee, new byte[0], Blockchain.getRemainingEnergy());

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
        StakerStorageObjects.PendingTransfer transfer = StakerRegistryStorage.getPendingTransfer(id);
        requireNonNull(transfer);
        Address caller = Blockchain.getCaller();

        // only the initiator can finalize the transfer, mainly because
        // the pool registry needs to keep track of stake transfers.
        require(caller.equals(transfer.initiator));

        // lock-up period check
        require(Blockchain.getBlockNumber() >= transfer.blockNumber + TRANSFER_LOCK_UP_PERIOD);

        // remove the transfer
        StakerRegistryStorage.putPendingTransfer(id, null);

        // credit the stake to the designated pool of the recipient
        Address toStaker = transfer.toStaker;
        // deduct the fee from transfer amount
        BigInteger remainingTransferValue = transfer.value.subtract(transfer.fee);

        StakerStorageObjects.StakeInfo stakeInfo = StakerRegistryStorage.getStakerStakeInfo(toStaker);
        stakeInfo.otherStake = stakeInfo.otherStake.add(remainingTransferValue);
        StakerRegistryStorage.putStakerStakeInfo(toStaker, stakeInfo);

        // transfer the fee to the caller
        secureCall(caller, transfer.fee, new byte[0], Blockchain.getRemainingEnergy());

        StakerRegistryEvents.finalizedDelegationTransfer(id);
    }

    /**
     * Updates the state of a staker
     *
     * @param staker identity address of the staker
     * @param newState new state, true equals ACTIVE and false equals BROKEN
     */
    @Callable
    public static void setState(Address staker, boolean newState){
        requireStakerAndManager(staker, Blockchain.getCaller());
        boolean currentState = StakerRegistryStorage.getState(staker);
        if(currentState != newState) {
            StakerRegistryStorage.putState(staker, newState);
            StakerRegistryEvents.changedState(staker, newState);
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
    public static BigInteger getEffectiveStake(Address signingAddress, Address coinbaseAddress) {
        requireNonNull(signingAddress);
        requireNonNull(coinbaseAddress);
        requireNoValue();

        // if not a staker
        Address staker = StakerRegistryStorage.getIdentityAddress(signingAddress);
        if (staker == null) {
            return BigInteger.ZERO;
        }

        // if coinbase addresses do not match
        if (!StakerRegistryStorage.getStakerAddressInfo(staker).coinbaseAddress.equals(coinbaseAddress)) {
            return BigInteger.ZERO;
        }

        // if not active
        StakerStorageObjects.StakeInfo stakeInfo = StakerRegistryStorage.getStakerStakeInfo(staker);
        if (!isStakerActive(staker, stakeInfo.selfBondStake)) {
            return BigInteger.ZERO;
        }

        // query total stake
        BigInteger totalStake = stakeInfo.otherStake.add(stakeInfo.selfBondStake);

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
        StakerStorageObjects.StakeInfo stakeInfo = validateAndGetStakeInfo(staker);
        requireNoValue();

        // returns the sum of delegated stake and self bond stake
        return stakeInfo.otherStake.add(stakeInfo.selfBondStake);
    }

    /**
     * Bonds the stake to the staker. Any liquid coins, passed along the call, are counted as part of the self-bond stake.
     *
     * @param staker the address of the staker
     */
    @Callable
    public static void bond(Address staker){
        BigInteger amount = Blockchain.getValue();

        requirePositive(amount);
        requireStakerAndManager(staker, Blockchain.getCaller());

        StakerStorageObjects.StakeInfo stakeInfo = StakerRegistryStorage.getStakerStakeInfo(staker);

        stakeInfo.selfBondStake = stakeInfo.selfBondStake.add(amount);
        StakerRegistryStorage.putStakerStakeInfo(staker, stakeInfo);

        StakerRegistryEvents.bonded(staker, amount);
    }

    /**
     * Unbonds for a staker, After a successful unbond, the locked coins will be released to the original bonder (management address).
     * This is subject to lock-up period.
     *
     * @param staker the address of the staker
     * @param amount the amount of stake
     * @param fee the amount of stake that will be transferred to the account that invokes finalizeUndelegate
     * @return a pending un-delegate identifier
     */
    @Callable
    public static long unbond(Address staker, BigInteger amount, BigInteger fee){
        return unbondTo(staker, amount, Blockchain.getCaller(), fee);
    }

    /**
     * Unbonds for a staker, After a successful unbond, the locked coins will be released to the specified account.
     * This is subject to lock-up period.
     *
     * @param staker the address of the staker
     * @param amount the amount of stake
     * @param recipient the receiving address
     * @param fee the amount of stake that will be transferred to the account that invokes finalizeUndelegate
     * @return a pending un-delegate identifier
     */
    @Callable
    public static long unbondTo(Address staker, BigInteger amount, Address recipient, BigInteger fee){
        Address caller = Blockchain.getCaller();

        requireStakerAndManager(staker, caller);
        requirePositive(amount);
        requireNoValue();
        require(fee.signum() >= 0 && fee.compareTo(amount) <= 0);

        StakerStorageObjects.StakeInfo stakeInfo = StakerRegistryStorage.getStakerStakeInfo(staker);

        require(amount.compareTo(stakeInfo.selfBondStake) <= 0);

        stakeInfo.selfBondStake = stakeInfo.selfBondStake.subtract(amount);
        StakerRegistryStorage.putStakerStakeInfo(staker, stakeInfo);

        long id = nextUndelegateId++;
        StakerStorageObjects.PendingUndelegate undelegate = new StakerStorageObjects.PendingUndelegate(recipient, amount, fee, Blockchain.getBlockNumber());
        StakerRegistryStorage.putPendingUndelegte(id, undelegate);

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
        requireNoValue();
        requireNonNull(staker);
        return StakerRegistryStorage.getStakerStakeInfo(staker) != null;
    }

    /**
     * Returns whether a staker is active, subject to pre-defined rules, e.g. min_self_stake
     *
     * @param staker the address of staker
     * @return true if active, otherwise false
     */
    @Callable
    public static boolean isActive(Address staker) {
        StakerStorageObjects.StakeInfo stakeInfo = validateAndGetStakeInfo(staker);
        requireNoValue();

        return isStakerActive(staker, stakeInfo.selfBondStake);
    }

    /**
     * Returns the signing address of a staker.
     *
     * @param staker the address of the staker.
     * @return the signing address
     */
    @Callable
    public static Address getSigningAddress(Address staker) {
        requireNonNull(staker);
        StakerStorageObjects.AddressInfo addressInfo = StakerRegistryStorage.getStakerAddressInfo(staker);
        requireNonNull(addressInfo);
        requireNoValue();

        return addressInfo.signingAddress;
    }

    /**
     * Returns the coinbase address of a staker.
     *
     * @param staker the identity address of the staker.
     * @return the coinbase address
     */
    @Callable
    public static Address getCoinbaseAddress(Address staker) {
        requireNonNull(staker);
        StakerStorageObjects.AddressInfo addressInfo = StakerRegistryStorage.getStakerAddressInfo(staker);
        requireNonNull(addressInfo);
        requireNoValue();

        return addressInfo.coinbaseAddress;
    }

    private static void requireStakerAndManager(Address staker, Address manager) {
        requireNonNull(staker);
        Address managementAddress = StakerRegistryStorage.getManagementAddress(staker);
        require(managementAddress != null && managementAddress.equals(manager));
    }
    /**
     * Updates the signing address of a staker.
     * Can only be invoked by the management address.
     *
     * @param newSigningAddress the new signing address
     */
    @Callable
    public static void setSigningAddress(Address staker, Address newSigningAddress) {
        requireNonNull(newSigningAddress);
        requireNoValue();
        requireStakerAndManager(staker, Blockchain.getCaller());

        StakerStorageObjects.AddressInfo addressInfo = StakerRegistryStorage.getStakerAddressInfo(staker);

        if (!newSigningAddress.equals(addressInfo.signingAddress)) {
            // check last update
            long blockNumber = Blockchain.getBlockNumber();
            require(blockNumber >= addressInfo.lastSigningAddressUpdate + SIGNING_ADDRESS_COOLING_PERIOD);

            // check duplicated signing address
            require(StakerRegistryStorage.getIdentityAddress(newSigningAddress) == null);

            // the old signing address is removed and can be used again by another staker
            StakerRegistryStorage.putIdentityAddress(addressInfo.signingAddress, null);
            StakerRegistryStorage.putIdentityAddress(newSigningAddress, staker);

            addressInfo.signingAddress = newSigningAddress;
            addressInfo.lastSigningAddressUpdate = blockNumber;
            StakerRegistryStorage.putStakerAddressInfo(staker, addressInfo);

            StakerRegistryEvents.setSigningAddress(staker, newSigningAddress);
        }
    }

    /**
     * Updates the coinbase address of a staker.
     * Can only be invoked by the management address.
     *
     * @param newCoinbaseAddress the new coinbase address
     */
    @Callable
    public static void setCoinbaseAddress(Address staker, Address newCoinbaseAddress) {
        requireNonNull(newCoinbaseAddress);
        requireNoValue();
        requireStakerAndManager(staker, Blockchain.getCaller());

        StakerStorageObjects.AddressInfo addressInfo = StakerRegistryStorage.getStakerAddressInfo(staker);

        if (!newCoinbaseAddress.equals(addressInfo.coinbaseAddress)) {
            addressInfo.coinbaseAddress = newCoinbaseAddress;
            StakerRegistryStorage.putStakerAddressInfo(staker, addressInfo);
            StakerRegistryEvents.setCoinbaseAddress(staker, newCoinbaseAddress);
        }
    }

    @Callable
    public static BigInteger getSelfBondStake(Address staker) {
        StakerStorageObjects.StakeInfo stakeInfo = validateAndGetStakeInfo(staker);
        requireNoValue();
        return stakeInfo.selfBondStake;
    }

    @Fallback
    public static void fallback(){
        Blockchain.revert();
    }

    private static boolean isStakerActive(Address staker, BigInteger selfBondStake){
        return selfBondStake.compareTo(MIN_SELF_STAKE) >= 0 && StakerRegistryStorage.getState(staker);
    }

    private static void require(boolean condition) {
        // now implements as un-catchable
        Blockchain.require(condition);
    }

    // validate the staker has been registered and return its stake info
    private static StakerStorageObjects.StakeInfo validateAndGetStakeInfo(Address staker) {
        requireNonNull(staker);
        StakerStorageObjects.StakeInfo stakeInfo = StakerRegistryStorage.getStakerStakeInfo(staker);
        requireNonNull(stakeInfo);
        return stakeInfo;
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

    private static void secureCall(Address targetAddress, BigInteger value, byte[] data, long energyLimit) {
        Result result = Blockchain.call(targetAddress, value, data, energyLimit);
        require(result.isSuccess());
    }
}
