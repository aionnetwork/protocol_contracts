package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import avm.Result;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.tooling.abi.Fallback;
import org.aion.avm.tooling.abi.Initializable;
import org.aion.avm.userlib.AionMap;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;

import java.math.BigInteger;
import java.util.Map;

/**
 * A stake delegation registry manages a list of registered pools, is the endpoint
 * for delegators/pool owners to interact with different pools.
 * <p>
 * Workflow for pool operator:
 * - Register the staker as a pool;
 * - delegate stake to pool (self bond).
 */
public class PoolRegistry {

    // TODO: replace object graph-based collections with key-value storage

    // 1000 Aions
    public static final BigInteger MIN_SELF_STAKE = new BigInteger("1000000000000000000000");
    private static final BigInteger MIN_SELF_STAKE_PERCENTAGE = BigInteger.ONE;
    // todo check value
    public static final long COMMISSION_RATE_CHANGE_TIME_LOCK_PERIOD = 6 * 60 * 24 * 7;


    @Initializable
    private static Address stakerRegistry;

    // used for validating the sender address of a value transfer
    private static Address currentPoolCoinbaseAddress;

    private static byte[] poolCoinbaseContract;

    private static Map<Address, PoolState> pools = new AionMap<>();

    static {
        poolCoinbaseContract = hexStringToByteArray("00000872504b0304140008080800d4ad282b000000000000000000000000140004004d4554412d494e462f4d414e49464553542e4d46feca0000f34dcccb4c4b2d2ed10d4b2d2acecccfb35230d433e0e5f24dccccd375ce492c2eb65270e4e5e2e50200504b07082bc2bf352a00000028000000504b0304140008080800d4ad282b00000000000000000000000007000000412e636c6173736d525b53d340183ddb16d28600e52a888a777b51aa88152ca2b480b614418a748007270d4b08a4a9262933fe14df7cf35574a666d4f1d519ff938edfa6958b636692c99e3d7bf67ce7fb7efefef21d401a6506362b813144f7d4033565aa969e5aaeec71cd951064e859a9d5cc5ccdb02aaac3c704850ed0db59540faaa9d9ed6d9b3b4e86613856f4cf575577379535f4bce5729ddb99f83a4397a066cd9ab6afedaa862521c210d1b99b534d93db0cddb1f82935051d5064c8e854d00e298200ba193a4e5024f430b4f3d775d57418065a779ff09e896f2ae843bf8c5e0c304836710d9b3384629bf175056730242e1866e8ff9f6d0923a4ef18ba55af320463f1bc82f3b820e31c46197ac9fb2aaf522986a5cf5bdcd6dff8a482824bb82c74afd0451a55c730113b55daff43daca169a09ac72a76eba14c0355c173a3718e4635c429c82339c525dd348ccbf93ca4ce2a68c046e51632a3e4691b7dbfc80dbae823bb82d84c619c2d39a498edd19e26525dca354a88e39d555452af1adac82fb9814e4293a3fdda2b6c5b6b222b00ca6651a9787747847c123d19f341ed3491103811a25496d3cee43c9b5291d2a258739c19d5790125e0278726ad69a3c097972e8daaae5ec703b8c451905d138a6330cfdd5fd373605cfb02cb457c49c8485f62a39cad5b6a9d19d2557d5f697d4576b6ac5a4b55caad56d8d2f1826a72e0510a2f127540c18fd8510111284acd12a8f366200e31eba3e22dac0a087b389062e86de410abd4728486bda4804bf853d5cf5102b261b1823f0d0577d415f5218a27c22a2ea966c14cd27e4e1eea1ff17c6c4d1ee28823ed6f715e90d0f0f3e6366a381d94f88969be408b2986c91cb44160ea78ec98b6470a198fc01d9c35332f621d95b6c60e92d7a12a4f3fc65d94349c0addd23a30a02bf3022212da13048d83a6101b4fd01504b070883a60cdea902000022040000504b0304140008080800d4ad282b00000000000000000000000007000000422e636c6173739d54db4e1351145da73375ca5064d0522e2d5a0a423b148a200a02261463d2a4e80386a4f220433b966269491988bc18e32ff001121243627c8044a81182efbefb01fe4723ee33bd0d501363d399d97b9f7d5b7b9d737efcfe7606e03e1e31b08804c6a0ac6a5b5a38a36553e167cbab7ac290203034cf44a28ff5442ea9e787b803b92fd3131528fa9c8404171c2424196c8b111274866b53e96cdaa0d4f6c06224b8c02004820b4e5c872243448b138d7036c0869b4e3441e2522b832b108cd55a9837f2e96c6a923bb4c9e4d07ea1c1d2aa844e33f53c555d71a28ba7b7e1166969277c25ad9bb459093d327a4b85bddcda7731dbf686a1af4908303468f9bcb69dc8ad6f33a881d8e5914c46eb98a21c9b8a0119418438ca66191e0c51e157343f42a56dad856792c9bcbeb131c9d06851258cc818357d530ced9509ac69c64a38924e45b3869ed2f314e4aa6797f040c6388f6e9acd65370c2d6b2c68994d22409c25c2c83c6f6889d773dafa736d3943ba3c9fdbcc27f427e98c8e6e1a8348dc89e8c435d00ec043d26c7090de60d1f9b8882e5326b238dbb44620e93d499a87ec36fa36ab05dc50078ee0528523b80fc17f3450b4951d27ca8e5d4cfd02d7473805fe3ddba5dd13db87433d46476ce0c00c9aa2b70ca1452c4294cc0ac45b358d68a619e4d1f4b84715c79b9d294f451597465b70fef6ddce941a27d5be44cd783e97bb69aa76f3137613fe276afb363d7e16ab251cd985cf924fe9de8552cbb68f2d73911b5e88dc421e1fd07882de780177be9bb07868d2be67b116d03f5d5bf25a96bca78ea795728382f72b61e3b9bd3cf5093cf1c102c2d6d174c05ec498dddb5aa403704e1bdb26f1bfc77418c6dd32c40382c84fe74e05e205807e2b40ff2e715705c8f9787905a2bf2e44c56701a2f84e1d7395bc2141f155a028be1296d178a8807b562c8d10c7bcee22fa253a070c63742595baff5526e8f85f08eab944d0fb2bddf7d4ed5edcbb68b586fc1759e39cac89bf90d567216bdc3c50746595e10e93c6e8db79f974540b1f5653d20168a124fc8a2b4787cad16df5f75e2d54007393386d56b7ff01504b0708f73eff920f03000006060000504b0304140008080800d4ad282b00000000000000000000000007000000432e636c6173733d4ebb0ac240109c358991181f69d3d9a985e9ac44d0a0a0a542fa239e72122fa217f1b7ac040b3fc08f12d7082eec6386d9d97dbd1f4f004304048a5d1021dc8b8b8832a177d1aad0461de4ec9acaa351b976611182c974f167065f31af6e39970e7ec126d591d2ca8c0956b797f8f050f760c32734e25c9f8dd02611592109769c6fb879ebbc38a572ae32890e2aac651b84a8827f42935105359ee86bc5b5c54c5062c0e9dfd1b8fd2ea35d4a9d0f504b0708d8f1b600b0000000d8000000504b01021400140008080800d4ad282b2bc2bf352a000000280000001400040000000000000000000000000000004d4554412d494e462f4d414e49464553542e4d46feca0000504b01021400140008080800d4ad282b83a60cdea902000022040000070000000000000000000000000070000000412e636c617373504b01021400140008080800d4ad282bf73eff920f0300000606000007000000000000000000000000004e030000422e636c617373504b01021400140008080800d4ad282bd8f1b600b0000000d8000000070000000000000000000000000092060000432e636c617373504b05060000000004000400e500000077070000000000000021220000000000000000000000000000000000000000000000000000000000000000");
        System.arraycopy(Blockchain.getAddress().toByteArray(), 0, poolCoinbaseContract, poolCoinbaseContract.length - Address.LENGTH, Address.LENGTH);
    }

    @Callable
    public static Address getStakerRegistry() {
        requireNoValue();
        return stakerRegistry;
    }

    /**
     * Registers a pool in the registry.
     *
     * @param signingAddress the signing address fo the pool
     * @param commissionRate the pool commission rate with 4 decimal places of granularity (between [0, 1000000])
     * @param metaDataUrl url hosting the metadata json file
     * @param metaDataContentHash Blake2b hash of the json object hosted at the metadata url
     */
    @Callable
    public static void registerPool(Address signingAddress, int commissionRate, byte[] metaDataUrl, byte[] metaDataContentHash) {
        // sanity check
        require(commissionRate >= 0 && commissionRate <= 1000000);
        requireNoValue();
        requireNonNull(metaDataUrl);
        require(metaDataContentHash != null && metaDataContentHash.length == 32);

        Address caller = Blockchain.getCaller();

        // make sure no one has registered as a pool using this identity
        // same check is done in StakerRegistry
        require(!pools.containsKey(caller));

        // step 1: deploy a coinbase contract
        Result result = Blockchain.create(BigInteger.ZERO, poolCoinbaseContract, Blockchain.getRemainingEnergy());
        require(result.isSuccess());
        Address coinbaseAddress = new Address(result.getReturnData());

        // step 2: create a staker in the staker registry
        /*
        registerStaker(Address identityAddress, Address managementAddress, Address signingAddress, Address coinbaseAddress)
         */
        byte[] registerStakerCall = new ABIStreamingEncoder()
                .encodeOneString("registerStaker")
                .encodeOneAddress(caller)
                .encodeOneAddress(Blockchain.getAddress())
                .encodeOneAddress(signingAddress)
                .encodeOneAddress(coinbaseAddress)
                .toBytes();
        secureCall(stakerRegistry, BigInteger.ZERO, registerStakerCall, Blockchain.getRemainingEnergy());

        // step 3: update pool state
        PoolState ps = new PoolState(caller, coinbaseAddress, commissionRate, metaDataUrl, metaDataContentHash);
        pools.put(caller, ps);
        PoolRegistryEvents.registeredPool(caller, commissionRate, metaDataContentHash, metaDataUrl);
    }

    /**
     * Updates the signing address of a staker. Owner only.
     *
     * @param staker the staker address
     * @param newAddress the new signing address
     */
    @Callable
    public static void setSigningAddress(Address staker, Address newAddress) {
        requireNonNull(newAddress);
        requireNoValue();
        require(Blockchain.getCaller().equals(staker));
        requirePool(staker);

        byte[] data = new ABIStreamingEncoder()
                .encodeOneString("setSigningAddress")
                .encodeOneAddress(staker)
                .encodeOneAddress(newAddress)
                .toBytes();
        secureCall(stakerRegistry, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
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
        requirePool(pool);
        requirePositive(value);

        detectBlockRewards(pool);

        delegate(caller, pool, Blockchain.getValue(), true);
    }

    private static void delegate(Address delegator, Address pool, BigInteger value, boolean doDelegate) {
        PoolState ps = pools.get(pool);

        if (doDelegate) {
            byte[] data;
            if (delegator.equals(pool)) {
                data = new ABIStreamingEncoder()
                        .encodeOneString("bond")
                        .encodeOneAddress(pool)
                        .toBytes();
            } else {
                data = new ABIStreamingEncoder()
                        .encodeOneString("delegate")
                        .encodeOneAddress(pool)
                        .toBytes();
            }
            secureCall(stakerRegistry, value, data, Blockchain.getRemainingEnergy());
        }

        BigInteger previousStake = getOrDefault(ps.delegators, delegator, BigInteger.ZERO);
        ps.delegators.put(delegator, previousStake.add(value));

        // update rewards state machine
        ps.rewards.onDelegate(delegator, Blockchain.getBlockNumber(), value);

        // if after the delegation the pool becomes active, update the commission rate
        if (delegator.equals(ps.stakerAddress) && isSelfStakeSatisfied(pool)) {
            switchToActive(ps);
        }

        PoolRegistryEvents.delegated(delegator, pool, value);
    }

    /**
     * Revokes stake to a pool.
     *
     * @param pool   the pool address
     * @param amount the amount of stake to undelegate
     */
    @Callable
    public static long undelegate(Address pool, BigInteger amount) {
        requirePool(pool);
        requirePositive(amount);
        requireNoValue();

        detectBlockRewards(pool);

        return undelegate(Blockchain.getCaller(), pool, amount);
    }

    private static long undelegate(Address delegator, Address pool, BigInteger amount) {
        PoolState ps = pools.get(pool);

        BigInteger previousStake = getOrDefault(ps.delegators, delegator, BigInteger.ZERO);

        require(previousStake.compareTo(amount) >= 0);
        ps.delegators.put(delegator, previousStake.subtract(amount));

        byte[] data;
        if (delegator.equals(pool)) {
            data = new ABIStreamingEncoder()
                    .encodeOneString("unbondTo")
                    .encodeOneAddress(pool)
                    .encodeOneBigInteger(amount)
                    .encodeOneAddress(delegator)
                    .toBytes();
        } else {
            data = new ABIStreamingEncoder()
                    .encodeOneString("undelegateTo")
                    .encodeOneAddress(pool)
                    .encodeOneBigInteger(amount)
                    .encodeOneAddress(delegator)
                    .toBytes();
        }
        Result result = secureCall(stakerRegistry, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
        long id = new ABIDecoder(result.getReturnData()).decodeOneLong();

        // update rewards state machine
        ps.rewards.onUndelegate(delegator, Blockchain.getBlockNumber(), amount);

        boolean isActive = isSelfStakeSatisfied(pool);

        // if after the un-delegation the state of the pool changes, update the commission rate
        // undelegation from a delegator can make the pool go back into the active state
        if (!delegator.equals(ps.stakerAddress) && isActive) {
            switchToActive(ps);
        }// undelegation from a pool operator can make the pool go into the broken state
        else if (delegator.equals(ps.stakerAddress) && !isActive) {
            switchToBroken(ps);
        }

        PoolRegistryEvents.undelegated(id, delegator, pool, amount);
        return id;
    }

    /**
     * Delegates block rewards to a pool
     *
     * @param pool the pool address
     */
    @Callable
    public static void redelegate(Address pool) {
        Address caller = Blockchain.getCaller();
        requirePool(pool);
        requireNoValue();

        detectBlockRewards(pool);

        PoolState ps = pools.get(pool);

        // do a withdraw
        BigInteger amount = ps.rewards.onWithdraw(caller, Blockchain.getBlockNumber());
        if (caller.equals(ps.stakerAddress)) {
            amount = amount.add(ps.rewards.onWithdrawOperator());
        }

        // amount > 0
        if (amount.signum() == 1) {
            delegate(caller, pool, amount, true);
        }
    }

    private static class StakeTransfer {
        Address initiator;
        Address fromPool;
        Address toPool;
        BigInteger amount;

        public StakeTransfer(Address initiator, Address fromPool, Address toPool, BigInteger amount) {
            this.initiator = initiator;
            this.fromPool = fromPool;
            this.toPool = toPool;
            this.amount = amount;
        }
    }

    private static Map<Long, StakeTransfer> transfers = new AionMap<>();

    /**
     * Transfers delegation from one pool to another pool.
     *
     * @param fromPool the from pool address
     * @param toPool   the to pool address
     * @param amount   the amount of stake to transfer
     * @return the pending transfer id
     */
    @Callable
    public static long transferDelegation(Address fromPool, Address toPool, BigInteger amount) {
        Address caller = Blockchain.getCaller();
        requirePool(fromPool);
        requirePool(toPool);
        requirePositive(amount);
        requireNoValue();
        require(!fromPool.equals(toPool));
        // make sure the self bond stake value is not changing in either the fromPool or toPool
        require(!caller.equals(fromPool) && !caller.equals(toPool));

        detectBlockRewards(fromPool);
        detectBlockRewards(toPool);

        PoolState ps = pools.get(fromPool);
        BigInteger previousStake1 = getOrDefault(ps.delegators, caller, BigInteger.ZERO);

        require(previousStake1.compareTo(amount) >= 0);
        ps.delegators.put(caller, previousStake1.subtract(amount));

        // update rewards state machine
        ps.rewards.onUndelegate(caller, Blockchain.getBlockNumber(), amount);

        byte[] data = new ABIStreamingEncoder()
                .encodeOneString("transferDelegationTo")
                .encodeOneAddress(fromPool)
                .encodeOneAddress(toPool)
                .encodeOneBigInteger(amount)
                .encodeOneAddress(Blockchain.getAddress())
                .toBytes();

        Result result = secureCall(stakerRegistry, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
        long id = new ABIDecoder(result.getReturnData()).decodeOneLong();
        transfers.put(id, new StakeTransfer(caller, fromPool, toPool, amount));

        // transfer out of fromPool could make it broken
        // this call can only be from a delegator
        if (isSelfStakeSatisfied(fromPool)) {
            switchToActive(ps);
        }

        PoolRegistryEvents.transferredDelegation(id, caller, fromPool, toPool, amount);
        return id;
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

        return getOrDefault(pools.get(pool).delegators, delegator, BigInteger.ZERO);
    }

    /**
     * Returns the self-bond stake to a pool.
     *
     * @param pool the pool address
     * @return the amount of stake
     */
    @Callable
    public static BigInteger getSelfStake(Address pool) {
        requirePool(pool);
        requireNoValue();

        PoolState ps = pools.get(pool);
        return getOrDefault(ps.delegators, ps.stakerAddress, BigInteger.ZERO);
    }

    /**
     * Returns the total stake of a pool.
     *
     * @param pool the pool address
     * @return the amount of stake
     */
    @Callable
    public static BigInteger getTotalStake(Address pool) {
        requirePool(pool);
        requireNoValue();
        return getTotalStakeCall(pool);
    }

    private static BigInteger getTotalStakeCall(Address pool){
        byte[] data = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool)
                .toBytes();
        Result result = secureCall(stakerRegistry, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
        return new ABIDecoder(result.getReturnData()).decodeOneBigInteger();
    }

    /**
     * Finalizes an undelegate operation.
     *
     * @param id pending undelegation id
     */
    @Callable
    public static void finalizeUndelegate(long id) {
        requireNoValue();

        byte[] data = new ABIStreamingEncoder()
                .encodeOneString("finalizeUndelegate")
                .encodeOneLong(id)
                .toBytes();
        secureCall(stakerRegistry, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
    }

    /**
     * Finalizes a transfer operation.
     *
     * @param id pending transfer id
     */
    @Callable
    public static void finalizeTransfer(long id) {
        requireNoValue();

        require(transfers.containsKey(id));

        StakeTransfer transfer = transfers.remove(id);

        byte[] data = new ABIStreamingEncoder()
                    .encodeOneString("finalizeTransfer")
                    .encodeOneLong(id)
                    .toBytes();

        secureCall(stakerRegistry, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());

        delegate(transfer.initiator, transfer.toPool, transfer.amount, false);
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

        return getOrDefault(pools.get(pool).autoRewardsDelegationDelegators, delegator, -1);
    }

    /**
     * Enables auto-redelegation on a pool.
     *
     * @param pool the pool address
     * @param feePercentage the auto-redelegation fee
     */
    @Callable
    public static void enableAutoRewardsDelegation(Address pool, int feePercentage) {
        requirePool(pool);
        require(feePercentage >= 0 && feePercentage <= 100);
        requireNoValue();

        Address caller = Blockchain.getCaller();
        pools.get(pool).autoRewardsDelegationDelegators.put(caller, feePercentage);
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
        pools.get(pool).autoRewardsDelegationDelegators.remove(caller);
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
        requirePool(pool);
        requireNonNull(delegator);
        requireNoValue();

        detectBlockRewards(pool);

        // check auto-redelegation authorization
        PoolState ps = pools.get(pool);
        require(ps.autoRewardsDelegationDelegators.containsKey(delegator));

        // do a withdraw
        BigInteger amount = ps.rewards.onWithdraw(delegator, Blockchain.getBlockNumber());
        if (delegator.equals(ps.stakerAddress)) {
            amount = amount.add(ps.rewards.onWithdrawOperator());
        }

        Blockchain.println("Auto delegation: rewards = " + amount);

        // amount > 0
        if (amount.signum() == 1) {
            // rounded down
            BigInteger fee = (amount.multiply(BigInteger.valueOf(ps.autoRewardsDelegationDelegators.get(delegator)))).divide(BigInteger.valueOf(100));
            BigInteger remaining = amount.subtract(fee);

            Blockchain.println("Auto delegation: fee = " + fee + ", remaining = " + remaining);

            // transfer fee to the caller
            secureCall(Blockchain.getCaller(), fee, new byte[0], Blockchain.getRemainingEnergy());

            delegate(delegator, pool, remaining, true);
        }
    }

    /**
     * Delegates to a pool and enables auto-redelegation.
     *
     * @param pool the pool address
     * @param fee the auto-redelegation fee
     */
    @Callable
    public static void delegateAndEnableAutoRedelegation(Address pool, int fee) {
        requirePool(pool);
        require(fee >= 0 && fee <= 100);
        requirePositive(Blockchain.getValue());

        delegate(Blockchain.getCaller(), pool, Blockchain.getValue(), true);
        enableAutoRewardsDelegation(pool, fee);
    }

    /**
     * Returns the outstanding rewards of a delegator.
     *
     * @param pool      the pool address
     * @param delegator the delegator address
     * @return the amount of outstanding rewards
     */
    public static BigInteger getRewards(Address pool, Address delegator) {
        requirePool(pool);
        requireNonNull(delegator);
        requireNoValue();

        return pools.get(pool).rewards.getRewards(delegator, Blockchain.getBlockNumber());
    }

    /**
     * Withdraws block rewards from one pool.
     *
     * @param pool the pool address
     */
    @Callable
    public static BigInteger withdraw(Address pool) {
        Address caller = Blockchain.getCaller();
        requirePool(pool);
        requireNoValue();

        detectBlockRewards(pool);

        // query withdraw amount from rewards state machine
        PoolState ps = pools.get(pool);
        BigInteger amount = ps.rewards.onWithdraw(caller, Blockchain.getBlockNumber());
        if (caller.equals(ps.stakerAddress)) {
            amount = amount.add(ps.rewards.onWithdrawOperator());
        }

        // do a transfer if amount > 0
        if (amount.signum() == 1) {
            secureCall(caller, amount, new byte[0], Blockchain.getRemainingEnergy());
        }
        PoolRegistryEvents.withdrew(caller, pool, amount);
        return amount;
    }

    @Callable
    public static long requestCommissionRateChange(Address pool, int newCommissionRate) {
        requireNoValue();
        requirePool(pool);
        // 4 decimal places granularity for commission rate
        require(newCommissionRate >= 0 && newCommissionRate <= 1000000);
        require(pools.get(pool).stakerAddress.equals(Blockchain.getCaller()));

        long id = nextCommissionRateUpdateRequestId++;
        pendingCommissionUpdates.put(id, new CommissionUpdate(pool, newCommissionRate, Blockchain.getBlockNumber()));

        PoolRegistryEvents.requestedCommissionRateChange(id, pool, newCommissionRate);

        return id;
    }

    @Callable
    public static void finalizeCommissionRateChange(long id){
        requireNoValue();

        // check existence
        CommissionUpdate commissionUpdate = pendingCommissionUpdates.get(id);
        requireNonNull(commissionUpdate);

        // lock-up period check
        require(Blockchain.getBlockNumber() >= commissionUpdate.blockNumber + COMMISSION_RATE_CHANGE_TIME_LOCK_PERIOD);

        PoolState ps = pools.get(commissionUpdate.pool);
        // only the pool owner can finalize the new commission rate
        require(ps.stakerAddress.equals(Blockchain.getCaller()));

        // commission rate in pool state is updated even in broken state to stay consistent with other meta data updates performed by the pool owner
        ps.commissionRate = commissionUpdate.newCommissionRate;

        // remove request
        pendingCommissionUpdates.remove(id);

        // make sure the pool is active, so that pool owner can't change the commission rate after it has been set to 0 as a punishment
        // if the pool is not active, the commission fee in rewards is set when it becomes active
        if(isSelfStakeSatisfied(commissionUpdate.pool)) {
            ps.rewards.setCommissionRate(commissionUpdate.newCommissionRate);
        }

        // generate finalization event
        PoolRegistryEvents.finalizedCommissionRateChange(id);
    }

    @Callable
    public static void updateMetaDataUrl(Address pool, byte[] newMetaDataUrl){
        requireNoValue();
        requirePool(pool);
        requireNonNull(newMetaDataUrl);
        PoolState ps = pools.get(pool);
        require(ps.stakerAddress.equals(Blockchain.getCaller()));
        ps.metaDataUrl = newMetaDataUrl;
        PoolRegistryEvents.updatedMetaDataUrl(pool, newMetaDataUrl);
    }

    @Callable
    public static void updateMetaDataContentHash(Address pool, byte[] newMetaDataContentHash){
        requireNoValue();
        requirePool(pool);
        require(newMetaDataContentHash != null && newMetaDataContentHash.length == 32);
        PoolState ps = pools.get(pool);
        require(ps.stakerAddress.equals(Blockchain.getCaller()));
        ps.metaDataContentHash = newMetaDataContentHash;
        PoolRegistryEvents.updatedMetaDataContentHash(pool, newMetaDataContentHash);
    }

    @Callable
    public static byte[] getPoolInfo(Address pool) {
        requirePool(pool);
        requireNoValue();
        PoolState ps = pools.get(pool);
        return new ABIStreamingEncoder()
                .encodeOneAddress(ps.stakerAddress)
                .encodeOneAddress(ps.coinbaseAddress)
                .encodeOneInteger(ps.commissionRate)
                .encodeOneByteArray(ps.metaDataUrl)
                .encodeOneByteArray(ps.metaDataContentHash)
                .toBytes();
    }

    /**
     * Returns the status of a pool.
     *
     * @param pool the pool address.
     * @return
     */
    @Callable
    public static String getPoolStatus(Address pool) {
        requirePool(pool);
        requireNoValue();
        return isSelfStakeSatisfied(pool) ? "ACTIVE" : "BROKEN";
    }

    @Fallback
    public static void fallback(){
        if(!Blockchain.getCaller().equals(currentPoolCoinbaseAddress)) {
            Blockchain.revert();
        }
    }

    private static boolean isSelfStakeSatisfied(Address pool) {
        requirePool(pool);

        BigInteger totalStake = getTotalStakeCall(pool);
        PoolState ps = pools.get(pool);
        BigInteger selfStake = getOrDefault(ps.delegators, ps.stakerAddress, BigInteger.ZERO);

        return selfStake.compareTo(MIN_SELF_STAKE) >= 0 &&
                (selfStake.multiply(BigInteger.valueOf(100))).divide(totalStake).compareTo(MIN_SELF_STAKE_PERCENTAGE) >= 0;
    }

    private static void switchToActive(PoolState ps) {
        if(ps.rewards.isFeeSetToZero() && ps.commissionRate != 0) {
            ps.rewards.setCommissionRate(ps.commissionRate);
            PoolRegistryEvents.changedPoolState(ps.stakerAddress, true);
        }
    }

    private static void switchToBroken(PoolState ps) {
        if(!ps.rewards.isFeeSetToZero()) {
            ps.rewards.setCommissionRate(0);
            PoolRegistryEvents.changedPoolState(ps.stakerAddress, false);
        }
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
        require(pool != null && pools.containsKey(pool));
    }

    private static void requirePositive(BigInteger num) {
        require(num != null && num.compareTo(BigInteger.ZERO) > 0);
    }

    private static byte[] hexStringToByteArray(String s) {
        // TODO: use static variable
        int[] map = new int[256];
        int value = 0;
        for (char c : "0123456789abcdef".toCharArray()) {
            map[c] = value++;
        }

        char[] chars = s.toCharArray();
        int length = chars.length;
        byte[] result = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            result[i / 2] = (byte) ((map[chars[i]] << 4) + map[chars[i + 1]]);
        }
        return result;
    }

    private static Result secureCall(Address targetAddress, BigInteger value, byte[] data, long energyLimit) {
        Result result = Blockchain.call(targetAddress, value, data, energyLimit);
        require(result.isSuccess());
        return result;
    }


    private static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultValue) {
        if (map.containsKey(key)) {
            return map.get(key);
        } else {
            return defaultValue;
        }
    }

    private static void detectBlockRewards(Address pool) {
        PoolState ps = pools.get(pool);

        BigInteger balance = Blockchain.getBalance(ps.coinbaseAddress);
        // balance > 0
        if (balance.signum() == 1) {
            byte[] data = new ABIStreamingEncoder()
                    .encodeOneString("transfer")
                    .encodeOneBigInteger(balance)
                    .toBytes();

            // pool's coinbase address is stored for the re-entrant call to ensure only coinbase addresses can transfer value to the PoolRegistry
            currentPoolCoinbaseAddress = ps.coinbaseAddress;
            secureCall(ps.coinbaseAddress, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
            currentPoolCoinbaseAddress = null;

            ps.rewards.onBlock(Blockchain.getBlockNumber(), balance);

            Blockchain.println("New block rewards: " + balance);
        }
    }

    private static class CommissionUpdate {
        Address pool;
        int newCommissionRate;
        long blockNumber;

        public CommissionUpdate(Address pool, int newCommissionRate, long blockNumber) {
            this.pool = pool;
            this.newCommissionRate = newCommissionRate;
            this.blockNumber = blockNumber;
        }
    }

    private static Map<Long, CommissionUpdate> pendingCommissionUpdates = new AionMap<>();
    private static long nextCommissionRateUpdateRequestId = 0;
}
