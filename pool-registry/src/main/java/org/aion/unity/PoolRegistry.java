package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import avm.Result;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.tooling.abi.Fallback;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;

import java.math.BigInteger;

/**
 * A stake delegation registry manages a list of registered pools, is the endpoint
 * for delegators/pool owners to interact with different pools.
 * <p>
 * Workflow for pool operator:
 * - Register the staker as a pool;
 * - delegate stake to pool (self bond).
 */
public class PoolRegistry {

    private static final BigInteger MIN_SELF_STAKE; // 1000 Aions
    private static final BigInteger MIN_SELF_STAKE_PERCENTAGE; // 1%
    // todo check value
    private static final long COMMISSION_RATE_CHANGE_TIME_LOCK_PERIOD; // 6 * 60 * 24 * 7

    private static final Address STAKER_REGISTRY;

    // used for validating the sender address of a value transfer
    private static Address reentrantPoolCoinbaseAddress;

    // used for keeping track of the reentrant value transfers from the reentrantPoolCoinbaseAddress and STAKER_REGISTRY
    private static BigInteger reentrantValueTransferAmount;

    private static long nextCommissionRateUpdateRequestId = 0;

    static {
        ABIDecoder decoder = new ABIDecoder(Blockchain.getData());
        STAKER_REGISTRY = decoder.decodeOneAddress();
        MIN_SELF_STAKE = decoder.decodeOneBigInteger();
        MIN_SELF_STAKE_PERCENTAGE = decoder.decodeOneBigInteger();
        COMMISSION_RATE_CHANGE_TIME_LOCK_PERIOD = decoder.decodeOneLong();

        byte[] poolCoinbaseContract = decoder.decodeOneByteArray();
        System.arraycopy(Blockchain.getAddress().toByteArray(), 0, poolCoinbaseContract, poolCoinbaseContract.length - Address.LENGTH, Address.LENGTH);
        PoolRegistryStorage.putCoinbaseContractBytes(poolCoinbaseContract);
        PoolRegistryEvents.poolRegistryDeployed(STAKER_REGISTRY, MIN_SELF_STAKE, MIN_SELF_STAKE_PERCENTAGE, COMMISSION_RATE_CHANGE_TIME_LOCK_PERIOD);
    }

    /**
     * Registers a pool in the registry.
     * Note that the minimum self bond value should be passed along the call.
     *
     * @param signingAddress the signing address fo the pool
     * @param commissionRate the pool commission rate with 4 decimal places of granularity (between [0, 1000000])
     * @param metaDataUrl url hosting the metadata json file
     * @param metaDataContentHash Blake2b hash of the json object hosted at the metadata url
     */
    @Callable
    public static void registerPool(Address signingAddress, int commissionRate, byte[] metaDataUrl, byte[] metaDataContentHash) {
        // sanity check
        requireValidPercentage(commissionRate);
        requireNonNull(metaDataUrl);
        require(metaDataContentHash != null && metaDataContentHash.length == 32);

        // ensure minimum self stake is passed to the contract
        BigInteger selfStake = Blockchain.getValue();
        require(selfStake.compareTo(MIN_SELF_STAKE) >= 0);

        Address caller = Blockchain.getCaller();

        // make sure no one has registered as a pool using this identity
        // same check is done in StakerRegistry
        require(PoolRegistryStorage.getPoolMetaData(caller) == null);

        byte[] poolCoinbaseContract = PoolRegistryStorage.getCoinbaseContractBytes();

        // step 1: deploy a coinbase contract
        Result result = Blockchain.create(BigInteger.ZERO, poolCoinbaseContract, Blockchain.getRemainingEnergy());
        require(result.isSuccess());
        Address coinbaseAddress = new Address(result.getReturnData());

        // step 2: create a staker in the staker registry
        /*
        registerStaker(Address identityAddress, Address managementAddress, Address signingAddress, Address coinbaseAddress)
         */
        String methodName = "registerStaker";
        // encoded data is directly written to the byte array to reduce energy usage
        byte[] registerStakerCall = new byte[getStringSize(methodName) + getAddressSize() * 4];
        new ABIStreamingEncoder(registerStakerCall)
                .encodeOneString(methodName)
                .encodeOneAddress(caller)
                .encodeOneAddress(Blockchain.getAddress())
                .encodeOneAddress(signingAddress)
                .encodeOneAddress(coinbaseAddress);
        secureCall(STAKER_REGISTRY, selfStake, registerStakerCall, Blockchain.getRemainingEnergy());

        // pool is initialized in active state
        PoolStorageObjects.PoolRewards rewards = new PoolStorageObjects.PoolRewards(coinbaseAddress, commissionRate);

        PoolStorageObjects.DelegatorInfo delegatorInfo = new PoolStorageObjects.DelegatorInfo();
        PoolRewardsStateMachine stateMachine = new PoolRewardsStateMachine(rewards);

        // step 3: update the self bond stake
        stateMachine.onDelegate(delegatorInfo, Blockchain.getBlockNumber(), selfStake);

        // step 4: store pool info
        PoolRegistryStorage.putDelegator(caller, caller, delegatorInfo);
        PoolRegistryStorage.putPoolRewards(caller, rewards);
        PoolRegistryStorage.putPoolMetaData(caller, metaDataContentHash, metaDataUrl);

        PoolRegistryEvents.registeredPool(caller, commissionRate, metaDataContentHash, metaDataUrl);
    }

    /**
     * Delegates stake to a pool.
     *
     * @param pool the pool address
     */
    @Callable
    public static void delegate(Address pool) {
        Address caller = Blockchain.getCaller();
        BigInteger value = Blockchain.getValue();

        PoolStorageObjects.PoolRewards poolRewards = validateAndGetPoolRewards(pool);

        requirePositive(value);

        PoolRewardsStateMachine stateMachine = new PoolRewardsStateMachine(poolRewards);
        detectBlockRewards(stateMachine);

        PoolStorageObjects.DelegatorInfo delegatorInfo = PoolRegistryStorage.getDelegator(pool, caller);

        delegate(caller, pool, value, true, stateMachine, delegatorInfo);
    }

    private static void delegate(Address delegator, Address pool, BigInteger value, boolean doDelegate, PoolRewardsStateMachine stateMachine, PoolStorageObjects.DelegatorInfo delegatorInfo) {

        BigInteger totalStakeAfterDelegation = stateMachine.currentPoolRewards.accumulatedStake.add(value);
        BigInteger poolSelfStake = getSelfStake(pool);

        // delegators should not be able to put the pool into a broken state by delegating an amount over the capacity,
        // or delegate to a pool in broken state
        if(!delegator.equals(pool) && !isSelfStakeSatisfied(poolSelfStake, totalStakeAfterDelegation, stateMachine.currentPoolRewards.pendingStake)){
            Blockchain.revert();
        }

        if (doDelegate) {
            String methodName;
            if (delegator.equals(pool)) {
                methodName = "bond";
            } else {
                methodName = "delegate";
            }
            // encoded data is directly written to the byte array to reduce energy usage
            byte[] data = new byte[getStringSize(methodName) + getAddressSize()];
            new ABIStreamingEncoder(data)
                    .encodeOneString(methodName)
                    .encodeOneAddress(pool);
            secureCall(STAKER_REGISTRY, value, data, Blockchain.getRemainingEnergy());
        }

        // update rewards state machine and delegator info
        stateMachine.onDelegate(delegatorInfo, Blockchain.getBlockNumber(), value);

        // update delegator information in storage.
        PoolRegistryStorage.putDelegator(pool, delegator, delegatorInfo);

        // if the pool was broken and delegation is from the pool operator, it might go into an active state
        if (delegator.equals(pool) && !stateMachine.currentPoolRewards.isActive) {
            BigInteger poolStake = poolSelfStake.add(value);
            if (isSelfStakeSatisfied(poolStake, totalStakeAfterDelegation, stateMachine.currentPoolRewards.pendingStake)) {
                // set pool state as active
                stateMachine.currentPoolRewards.isActive = true;
                setStateInStakerRegistry(pool, true);
            }
        }

        PoolRegistryStorage.putPoolRewards(pool, stateMachine.currentPoolRewards);

        PoolRegistryEvents.delegated(delegator, pool, value);
    }

    /**
     * Revokes stake to a pool.
     *
     * @param pool   the pool address
     * @param amount the amount of stake to undelegate
     * @param fee the amount of stake that will be transferred to the account that invokes finalizeUndelegate
     */
    @Callable
    public static long undelegate(Address pool, BigInteger amount, BigInteger fee) {
        PoolStorageObjects.PoolRewards poolRewards = validateAndGetPoolRewards(pool);

        requirePositive(amount);
        require(fee.signum() >= 0 && fee.compareTo(amount) <= 0);
        requireNoValue();

        PoolRewardsStateMachine stateMachine = new PoolRewardsStateMachine(poolRewards);
        detectBlockRewards(stateMachine);

        Address delegator = Blockchain.getCaller();

        PoolStorageObjects.DelegatorInfo delegatorInfo = PoolRegistryStorage.getDelegator(pool, delegator);
        BigInteger previousStake = delegatorInfo.stake;

        require(previousStake.compareTo(amount) >= 0);

        BigInteger poolStake = getSelfStake(pool);

        String methodName;
        if (delegator.equals(pool)) {
            methodName = "unbondTo";
        } else {
            methodName = "undelegateTo";
        }
        // encoded data is directly written to the byte array to reduce energy usage
        byte[] data = new byte[getStringSize(methodName) + getBigIntegerSize() * 2 + getAddressSize() * 2];
        new ABIStreamingEncoder(data)
                .encodeOneString(methodName)
                .encodeOneAddress(pool)
                .encodeOneBigInteger(amount)
                .encodeOneAddress(delegator)
                .encodeOneBigInteger(fee);
        Result result = secureCall(STAKER_REGISTRY, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
        long id = new ABIDecoder(result.getReturnData()).decodeOneLong();

        // update rewards state machine and delegator info
        stateMachine.onUndelegate(delegatorInfo, Blockchain.getBlockNumber(), amount);

        PoolRegistryStorage.putDelegator(pool, delegator, delegatorInfo);

        // After the un-delegation the state of the pool might change
        // undelegation from a delegator can make a broken pool go into the active state
        if (!delegator.equals(pool) && !poolRewards.isActive && isSelfStakeSatisfied(poolStake, poolRewards.accumulatedStake, poolRewards.pendingStake)) {
            stateMachine.currentPoolRewards.isActive = true;
            setStateInStakerRegistry(pool, true);
        }// undelegation from a pool operator can make an active pool go into the broken state
        else if (delegator.equals(pool) && poolRewards.isActive && !isSelfStakeSatisfied(poolStake.subtract(amount), poolRewards.accumulatedStake, poolRewards.pendingStake)) {
            stateMachine.currentPoolRewards.isActive = false;
            setStateInStakerRegistry(pool, false);
        }

        PoolRegistryStorage.putPoolRewards(pool, stateMachine.currentPoolRewards);

        PoolRegistryEvents.undelegated(id, delegator, pool, amount, fee);
        return id;
    }

    /**
     * Delegates block rewards to a pool
     *
     * @param pool the pool address
     */
    @Callable
    public static void redelegateRewards(Address pool) {
        Address caller = Blockchain.getCaller();
        PoolStorageObjects.PoolRewards poolRewards = validateAndGetPoolRewards(pool);
        requireNoValue();

        PoolRewardsStateMachine stateMachine = new PoolRewardsStateMachine(poolRewards);
        detectBlockRewards(stateMachine);

        PoolStorageObjects.DelegatorInfo delegatorInfo = PoolRegistryStorage.getDelegator(pool, caller);
        // do a withdraw
        BigInteger amount = stateMachine.onWithdraw(delegatorInfo, Blockchain.getBlockNumber());
        if (caller.equals(pool)) {
            amount = amount.add(stateMachine.onWithdrawOperator());
        }

        // amount > 0
        if (amount.signum() == 1) {
            delegate(caller, pool, amount, true, stateMachine, delegatorInfo);
        }
    }

    /**
     * Transfers delegation from one pool to another pool.
     *
     * @param fromPool the from pool address
     * @param toPool   the to pool address
     * @param amount   the amount of stake to transfer
     * @param fee the amount of stake that will be transferred to the account that invokes finalizeTransfer
     * @return the pending transfer id
     */
    @Callable
    public static long transferDelegation(Address fromPool, Address toPool, BigInteger amount, BigInteger fee) {
        Address caller = Blockchain.getCaller();

        PoolStorageObjects.PoolRewards fromPoolRewards = validateAndGetPoolRewards(fromPool);
        PoolStorageObjects.PoolRewards toPoolRewards = validateAndGetPoolRewards(toPool);

        requirePositive(amount);
        requireNoValue();
        require(!fromPool.equals(toPool));
        // should not be able to transfer to a broken pool
        require(toPoolRewards.isActive);

        // make sure the self bond stake value is not changing in either the fromPool or toPool
        require(!caller.equals(fromPool) && !caller.equals(toPool));

        // fee should be less than the amount for the delegate to be successful and not revert
        require(fee.signum() >= 0 && fee.compareTo(amount) < 0);

        // ensure transfer will not put the to pool in a broken state
        toPoolRewards.pendingStake = toPoolRewards.pendingStake.add(amount).subtract(fee);
        require(isSelfStakeSatisfied(getSelfStake(toPool), toPoolRewards.accumulatedStake, toPoolRewards.pendingStake));

        PoolRewardsStateMachine stateMachine = new PoolRewardsStateMachine(fromPoolRewards);
        PoolStorageObjects.DelegatorInfo delegatorInfo = PoolRegistryStorage.getDelegator(fromPool, caller);

        // movement of stake to toPool happens during finalization,
        // so detecting the block rewards for toPool happens during finalization
        detectBlockRewards(stateMachine);

        BigInteger previousStake1 = delegatorInfo.stake;
        require(previousStake1.compareTo(amount) >= 0);

        // update rewards state machine
        stateMachine.onUndelegate(delegatorInfo, Blockchain.getBlockNumber(), amount);
        PoolRegistryStorage.putDelegator(fromPool, caller, delegatorInfo);

        String methodName = "transferDelegation";
        // encoded data is directly written to the byte array to reduce energy usage
        byte[] data = new byte[getStringSize(methodName) + getBigIntegerSize() * 2 + getAddressSize() * 3];
        new ABIStreamingEncoder(data)
                .encodeOneString(methodName)
                .encodeOneAddress(fromPool)
                .encodeOneAddress(toPool)
                .encodeOneBigInteger(amount)
                .encodeOneBigInteger(fee);

        Result result = secureCall(STAKER_REGISTRY, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
        long id = new ABIDecoder(result.getReturnData()).decodeOneLong();
        PoolStorageObjects.StakeTransfer transfer = new PoolStorageObjects.StakeTransfer(caller, fromPool, toPool, amount);
        PoolRegistryStorage.putPendingTransfer(id, transfer);

        // transfer out of a broken fromPool could make it active
        // this call can only be from a delegator
        if (!fromPoolRewards.isActive && isSelfStakeSatisfied(getSelfStake(fromPool), fromPoolRewards.accumulatedStake, fromPoolRewards.pendingStake)) {
            stateMachine.currentPoolRewards.isActive = true;
            setStateInStakerRegistry(fromPool, true);
        }

        PoolRegistryStorage.putPoolRewards(fromPool, fromPoolRewards);
        // update the pending stake in to pool to reflect the transfer value
        PoolRegistryStorage.putPoolRewards(toPool, toPoolRewards);

        PoolRegistryEvents.transferredDelegation(id, caller, fromPool, toPool, amount, fee);
        return id;
    }

    /**
     * Finalizes an undelegate operation.
     *
     * @param id pending undelegation id
     */
    @Callable
    public static void finalizeUndelegate(long id) {
        requireNoValue();

        String methodName = "finalizeUndelegate";
        // encoded data is directly written to the byte array to reduce energy usage
        byte[] data = new byte[getStringSize(methodName) + 1 + Long.BYTES];
        new ABIStreamingEncoder(data)
                .encodeOneString(methodName)
                .encodeOneLong(id);

        // call STAKER_REGISTRY to finalize the undelegate and transfer the value to the recipient
        secureCall(STAKER_REGISTRY, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());

        // At this point the StakerRegistry has transferred the fee amount to this contract in a re-entrant call.
        // This is a safe assumption since these two contracts are tightly coupled. Thus, the fee amount is not explicitly stored to save energy.
        assert reentrantValueTransferAmount != null;
        // transfer the fee to the caller
        secureCall(Blockchain.getCaller(), reentrantValueTransferAmount, new byte[0], Blockchain.getRemainingEnergy());
        reentrantValueTransferAmount = null;
    }

    /**
     * Finalizes a transfer operation.
     *
     * @param id pending transfer id
     */
    @Callable
    public static void finalizeTransfer(long id) {
        requireNoValue();
        // validate transfer exists
        PoolStorageObjects.StakeTransfer transfer = PoolRegistryStorage.getPendingTransfer(id);
        require(transfer != null);

        String methodName = "finalizeTransfer";
        // encoded data is directly written to the byte array to reduce energy usage
        byte[] data = new byte[getStringSize(methodName) + 1 + Long.BYTES];
        new ABIStreamingEncoder(data)
                .encodeOneString("finalizeTransfer")
                .encodeOneLong(id);

        secureCall(STAKER_REGISTRY, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());

        // At this point the StakerRegistry has transferred the fee amount to this contract in a re-entrant call.
        // This is a safe assumption since these two contracts are tightly coupled. Thus, the fee amount is not explicitly stored to save energy.
        assert reentrantValueTransferAmount != null;
        // transfer fee
        secureCall(Blockchain.getCaller(), reentrantValueTransferAmount, new byte[0], Blockchain.getRemainingEnergy());
        BigInteger remainingTransferValue = transfer.amount.subtract(reentrantValueTransferAmount);
        reentrantValueTransferAmount = null;

        // remove transfer
        PoolRegistryStorage.putPendingTransfer(id, null);
        PoolStorageObjects.PoolRewards rewards = PoolRegistryStorage.getPoolRewards(transfer.toPool);

        // subtract the transfer amount from pending stake
        assert remainingTransferValue.compareTo(rewards.pendingStake) <= 0;
        rewards.pendingStake = rewards.pendingStake.subtract(remainingTransferValue);

        PoolRewardsStateMachine stateMachine = new PoolRewardsStateMachine(rewards);
        PoolStorageObjects.DelegatorInfo delegatorInfo = PoolRegistryStorage.getDelegator(transfer.toPool, transfer.initiator);

        detectBlockRewards(stateMachine);

        delegate(transfer.initiator, transfer.toPool, remainingTransferValue, false, stateMachine, delegatorInfo);
    }

    /**
     * Withdraws block rewards from one pool.
     *
     * @param pool the pool address
     */
    @Callable
    public static BigInteger withdrawRewards(Address pool) {
        Address caller = Blockchain.getCaller();

        PoolStorageObjects.PoolRewards poolRewards = validateAndGetPoolRewards(pool);
        requireNoValue();

        PoolRewardsStateMachine stateMachine = new PoolRewardsStateMachine(poolRewards);
        detectBlockRewards(stateMachine);

        PoolStorageObjects.DelegatorInfo delegatorInfo = PoolRegistryStorage.getDelegator(pool, caller);
        // query withdraw amount from rewards state machine
        BigInteger amount = stateMachine.onWithdraw(delegatorInfo, Blockchain.getBlockNumber());
        if (caller.equals(pool)) {
            amount = amount.add(stateMachine.onWithdrawOperator());
        }

        // do a transfer if amount > 0
        if (amount.signum() == 1) {
            secureCall(caller, amount, new byte[0], Blockchain.getRemainingEnergy());
        }

        // remove the delegator from storage if the stake is zero and all the rewards have been withdrawn
        if(delegatorInfo.stake.equals(BigInteger.ZERO)) {
            delegatorInfo = null;
        }

        PoolRegistryStorage.putDelegator(pool, caller, delegatorInfo);
        PoolRegistryStorage.putPoolRewards(pool, stateMachine.currentPoolRewards);

        PoolRegistryEvents.withdrew(caller, pool, amount);
        return amount;
    }

    /**
     * Enables auto-redelegation on a pool.
     *
     * @param pool the pool address
     * @param feePercentage the auto-redelegation fee, with 4 decimal places of granularity (between [0, 1000000])
     */
    @Callable
    public static void enableAutoRewardsDelegation(Address pool, int feePercentage) {
        requirePool(pool);
        requireValidPercentage(feePercentage);
        requireNoValue();

        Address caller = Blockchain.getCaller();
        PoolRegistryStorage.putAutoDelegationFee(pool, caller, feePercentage);
        PoolRegistryEvents.enabledAutoRewardsDelegation(caller, pool, feePercentage);
    }

    /**
     * Disables auto-redelegation on a pool.
     *
     * @param pool the pool address
     */
    @Callable
    public static void disableAutoRewardsDedelegation(Address pool) {
        requirePool(pool);
        requireNoValue();

        Address caller = Blockchain.getCaller();
        // remove from storage
        PoolRegistryStorage.putAutoDelegationFee(pool, caller, -1);
        PoolRegistryEvents.disabledAutoRewardsDelegation(caller, pool);
    }

    /**
     * Delegates one delegator's block rewards to the pool. The caller
     * gets the auto-redelegation fee.
     *
     * @param pool the pool address
     * @param delegator the delegator address
     */
    @Callable
    public static void autoDelegateRewards(Address pool, Address delegator) {
        PoolStorageObjects.PoolRewards rewards = validateAndGetPoolRewards(pool);
        requireNonNull(delegator);
        requireNoValue();

        PoolRewardsStateMachine stateMachine = new PoolRewardsStateMachine(rewards);
        detectBlockRewards(stateMachine);

        int feePercentage = PoolRegistryStorage.getAutoDelegationFee(pool, delegator);
        // check auto-redelegation authorization, -1 indicates it was not found in storage
        require(feePercentage >= 0);

        PoolStorageObjects.DelegatorInfo delegatorInfo = PoolRegistryStorage.getDelegator(pool, delegator);
        // do a withdraw
        BigInteger amount = stateMachine.onWithdraw(delegatorInfo, Blockchain.getBlockNumber());
        if (delegator.equals(pool)) {
            amount = amount.add(stateMachine.onWithdrawOperator());
        }

        Blockchain.println("Auto delegation: rewards = " + amount);

        // amount > 0
        if (amount.signum() == 1) {
            // rounded down
            BigInteger fee = (amount.multiply(BigInteger.valueOf(feePercentage))).divide(BigInteger.valueOf(1000000));
            BigInteger remaining = amount.subtract(fee);

            Blockchain.println("Auto delegation: fee = " + fee + ", remaining = " + remaining);

            // transfer fee to the caller
            secureCall(Blockchain.getCaller(), fee, new byte[0], Blockchain.getRemainingEnergy());

            delegate(delegator, pool, remaining, true, stateMachine, delegatorInfo);
        }
    }

    @Callable
    public static long requestCommissionRateChange(int newCommissionRate) {
        requireNoValue();
        Address pool = Blockchain.getCaller();
        requirePool(pool);
        // 4 decimal places granularity for commission rate
        requireValidPercentage(newCommissionRate);

        long id = nextCommissionRateUpdateRequestId++;
        PoolRegistryStorage.putPendingCommissionUpdate(id, new PoolStorageObjects.CommissionUpdate(pool, newCommissionRate, Blockchain.getBlockNumber()));

        PoolRegistryEvents.requestedCommissionRateChange(id, pool, newCommissionRate);

        return id;
    }

    @Callable
    public static void finalizeCommissionRateChange(long id){
        requireNoValue();

        // check existence
        PoolStorageObjects.CommissionUpdate commissionUpdate = PoolRegistryStorage.getPendingCommissionUpdate(id);
        requireNonNull(commissionUpdate);

        // lock-up period check
        require(Blockchain.getBlockNumber() >= commissionUpdate.blockNumber + COMMISSION_RATE_CHANGE_TIME_LOCK_PERIOD);

        // only the pool owner can finalize the new commission rate
        require(commissionUpdate.pool.equals(Blockchain.getCaller()));

        PoolStorageObjects.PoolRewards rewards = PoolRegistryStorage.getPoolRewards(commissionUpdate.pool);
        // commission rate in pool rewards is updated even in broken state to stay consistent with other meta data updates performed by the pool owner
        rewards.commissionRate = commissionUpdate.newCommissionRate;

        // remove request
        PoolRegistryStorage.putPendingCommissionUpdate(id, null);

        PoolRewardsStateMachine stateMachine = new PoolRewardsStateMachine(rewards);
        detectBlockRewards(stateMachine);
        stateMachine.setCommissionRate(commissionUpdate.newCommissionRate);

        PoolRegistryStorage.putPoolRewards(commissionUpdate.pool, rewards);

        // generate finalization event
        PoolRegistryEvents.finalizedCommissionRateChange(id);
    }

    @Callable
    public static void updateMetaData(byte[] newMetaDataUrl, byte[] newMetaDataContentHash){
        requireNoValue();
        Address pool = Blockchain.getCaller();
        // validate pool exists
        byte[] metadata = PoolRegistryStorage.getPoolMetaData(pool);
        requireNonNull(metadata);

        // validate input
        requireNonNull(newMetaDataUrl);
        require(newMetaDataContentHash != null && newMetaDataContentHash.length == 32);

        PoolRegistryStorage.putPoolMetaData(pool, newMetaDataContentHash, newMetaDataUrl);

        PoolRegistryEvents.updatedMetaData(pool, newMetaDataUrl, newMetaDataContentHash);
    }

    /**
     * Updates the signing address of a staker. Owner only.
     *
     * @param newAddress the new signing address
     */
    @Callable
    public static void setSigningAddress(Address newAddress) {
        requireNonNull(newAddress);
        requireNoValue();
        Address staker = Blockchain.getCaller();
        requirePool(staker);

        String methodName = "setSigningAddress";
        // encoded data is directly written to the byte array to reduce energy usage
        byte[] data = new byte[getStringSize(methodName) + getAddressSize() * 2];
        new ABIStreamingEncoder(data)
                .encodeOneString(methodName)
                .encodeOneAddress(staker)
                .encodeOneAddress(newAddress);
        secureCall(STAKER_REGISTRY, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
    }

    /**
     * Returns the outstanding rewards of a delegator.
     *
     * @param pool      the pool address
     * @param delegator the delegator address
     * @return the amount of outstanding rewards
     */
    @Callable
    public static BigInteger getRewards(Address pool, Address delegator) {
        PoolStorageObjects.PoolRewards poolRewards = validateAndGetPoolRewards(pool);
        requireNonNull(delegator);
        requireNoValue();

        PoolRewardsStateMachine stateMachine = new PoolRewardsStateMachine(poolRewards);

        // update block rewards without transferring the balance
        BigInteger balance = Blockchain.getBalance(poolRewards.coinbaseAddress);

        if (balance.signum() == 1) {
            stateMachine.onBlock(Blockchain.getBlockNumber(), balance);
        }
        PoolStorageObjects.DelegatorInfo delegatorInfo = PoolRegistryStorage.getDelegator(pool, delegator);
        // query withdraw amount from rewards state machine.
        // Updated values are only stored when the rewards are withdrawn
        BigInteger amount = stateMachine.onWithdraw(delegatorInfo, Blockchain.getBlockNumber());
        if (delegator.equals(pool)) {
            amount = amount.add(stateMachine.onWithdrawOperator());
        }

        return amount;
    }

    /**
     * Returns the stake of a delegator to a pool.
     *
     * @param pool      the pool address
     * @param delegator the delegator address
     * @return the amount of stake
     */
    @Callable
    public static BigInteger getStake(Address pool, Address delegator) {
        requirePool(pool);
        requireNonNull(delegator);
        requireNoValue();

        return PoolRegistryStorage.getDelegator(pool, delegator).stake;
    }

    /**
     * Returns the total stake of a pool.
     *
     * @param pool the pool address
     * @return the amount of stake. returned array has two elements:
     * first element represents the total stake of the pool, and the second element represents the stake that was transferred but has not been finalized yet.
     */
    @Callable
    public static BigInteger[] getTotalStake(Address pool) {
        PoolStorageObjects.PoolRewards rewards = validateAndGetPoolRewards(pool);
        requireNoValue();
        return new BigInteger[]{rewards.accumulatedStake, rewards.pendingStake};
    }

    @Callable
    public static byte[] getPoolInfo(Address pool) {
        requireNoValue();
        PoolStorageObjects.PoolRewards rewards = validateAndGetPoolRewards(pool);
        BigInteger selfStake = getSelfStake(pool);
        byte[] metadata = PoolRegistryStorage.getPoolMetaData(pool);
        byte[] metaDataUrl = new byte[metadata.length - 32];
        byte[] metaDataHash = new byte[32];
        System.arraycopy(metadata, 0, metaDataHash, 0, 32);
        System.arraycopy(metadata, 32, metaDataUrl, 0, metaDataUrl.length);

        // byte[] encoded length = (byte) token + (short) length + array length
        byte[] info = new byte[getAddressSize() + (1 + Integer.BYTES) + (1 + 1) + (1 + Short.BYTES) * 2 + metadata.length];
        new ABIStreamingEncoder(info)
                .encodeOneAddress(rewards.coinbaseAddress)
                .encodeOneInteger(rewards.commissionRate)
                .encodeOneBoolean(isSelfStakeSatisfied(selfStake, rewards.accumulatedStake, rewards.pendingStake))
                .encodeOneByteArray(metaDataHash)
                .encodeOneByteArray(metaDataUrl);
        return info;
    }

    @Callable
    public static BigInteger getOutstandingRewards(Address pool){
        PoolStorageObjects.PoolRewards poolRewards = validateAndGetPoolRewards(pool);
        requireNoValue();
        return poolRewards.outstandingRewards;
    }

    /**
     * Returns the auto-redelegation fee set by a delegator, or -1 if not set.
     *
     * @param pool      the pool's address
     * @param delegator the delegator's address
     * @return the fee in percentage, or -1
     */
    @Callable
    public static int getAutoRewardsDelegationFee(Address pool, Address delegator) {
        requirePool(pool);
        requireNoValue();

        return PoolRegistryStorage.getAutoDelegationFee(pool, delegator);
    }

    @Callable
    public static Address getStakerRegistry() {
        requireNoValue();
        return STAKER_REGISTRY;
    }

    @Fallback
    public static void fallback(){
        Address caller = Blockchain.getCaller();
        if(!caller.equals(reentrantPoolCoinbaseAddress) && !caller.equals(STAKER_REGISTRY)) {
            Blockchain.revert();
        }
        assert reentrantValueTransferAmount == null;
        reentrantValueTransferAmount = Blockchain.getValue();
    }

    private static BigInteger getSelfStake(Address pool){
        return PoolRegistryStorage.getDelegator(pool, pool).stake;
    }

    // checks both minimum self bond percentage and minimum self bond value for pool
    private static boolean isSelfStakeSatisfied(BigInteger selfStake, BigInteger currentTotalStake, BigInteger pendingStake) {
        BigInteger totalStake = currentTotalStake.add(pendingStake);
        return selfStake.compareTo(MIN_SELF_STAKE) >= 0 &&
                (selfStake.multiply(BigInteger.valueOf(100))).divide(totalStake).compareTo(MIN_SELF_STAKE_PERCENTAGE) >= 0;
    }

    private static void setStateInStakerRegistry(Address pool, boolean state) {
        String methodName = "setState";
        byte[] txData = new byte[getStringSize(methodName) + getAddressSize() + (1 + 1)];
        new ABIStreamingEncoder(txData)
                .encodeOneString(methodName)
                .encodeOneAddress(pool)
                .encodeOneBoolean(state);
        secureCall(STAKER_REGISTRY, BigInteger.ZERO, txData, Blockchain.getRemainingEnergy());
    }

    private static void require(boolean condition) {
        Blockchain.require(condition);
    }

    private static void requireNonNull(Object obj) {
        require(obj != null);
    }

    private static void requireNoValue() {
        require(Blockchain.getValue().equals(BigInteger.ZERO));
    }

    private static void requirePool(Address pool) {
        // pool metadata is used to check existence since it's cheaper than retrieving its rewards
        require(pool != null && PoolRegistryStorage.getPoolMetaData(pool) != null);
    }

    // validates pool exists and returns its reward info
    private static PoolStorageObjects.PoolRewards validateAndGetPoolRewards(Address pool) {
        Blockchain.require(pool != null);
        PoolStorageObjects.PoolRewards poolRewards = PoolRegistryStorage.getPoolRewards(pool);
        Blockchain.require(poolRewards != null);
        return poolRewards;
    }

    private static void requirePositive(BigInteger num) {
        require(num != null && num.compareTo(BigInteger.ZERO) > 0);
    }

    // ensures the fee percentage is valid, with 4 decimal places
    private static void requireValidPercentage(int value){
        require(value >= 0 && value <= 1000000);
    }

    private static Result secureCall(Address targetAddress, BigInteger value, byte[] data, long energyLimit) {
        Result result = Blockchain.call(targetAddress, value, data, energyLimit);
        require(result.isSuccess());
        return result;
    }

    private static void detectBlockRewards(PoolRewardsStateMachine rewardsStateMachine) {
        Address coinbaseAddress = rewardsStateMachine.currentPoolRewards.coinbaseAddress;
        BigInteger balance = Blockchain.getBalance(coinbaseAddress);
        // balance > 0
        if (balance.signum() == 1) {
            String methodName = "transfer";
            // encoded data is directly written to the byte array to reduce energy usage
            byte[] data = new byte[getStringSize(methodName) + getBigIntegerSize()];
            new ABIStreamingEncoder(data)
                    .encodeOneString(methodName)
                    .encodeOneBigInteger(balance);

            // pool's coinbase address is stored for the re-entrant call to ensure only coinbase addresses can transfer value to the PoolRegistry
            reentrantPoolCoinbaseAddress = coinbaseAddress;
            secureCall(coinbaseAddress, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
            assert (reentrantValueTransferAmount.equals(balance));
            reentrantPoolCoinbaseAddress = null;
            reentrantValueTransferAmount = null;

            rewardsStateMachine.onBlock(Blockchain.getBlockNumber(), balance);

            Blockchain.println("New block rewards: " + balance);
        }
    }

    private static int getStringSize(String value){
        // (byte) token + (short) length + value length
        return 1 + Short.BYTES + value.getBytes().length;
    }

    private static int getAddressSize(){
        // (byte) token + Address length
        return 1 + 32;
    }

    private static int getBigIntegerSize(){
        // (byte) token + (byte) length + max BigInteger length
        return 1 + 1 + 32;
    }
}
