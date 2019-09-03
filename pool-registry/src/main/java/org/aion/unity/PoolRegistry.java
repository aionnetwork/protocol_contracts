package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import avm.Result;
import org.aion.avm.tooling.abi.Callable;
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
 * - Register a staker;
 * - Register the staker as a pool;
 * - Set the coinbase address to the address of the pool coinbase contract.
 */
public class PoolRegistry {

    // TODO: replace object graph-based collections with key-value storage

    public static final BigInteger MIN_SELF_STAKE = BigInteger.valueOf(1000L);

    @Initializable
    private static Address stakerRegistry;

    private static byte[] poolCoinbaseContract;

    private static Map<Address, PoolState> pools = new AionMap<>();

    static {
        poolCoinbaseContract = hexStringToByteArray("00000881504b0304140008080800d4ad282b000000000000000000000000140004004d4554412d494e462f4d414e49464553542e4d46feca0000f34dcccb4c4b2d2ed10d4b2d2acecccfb35230d433e0e5f24dccccd375ce492c2eb65270e4e5e2e50200504b07082bc2bf352a00000028000000504b0304140008080800d4ad282b00000000000000000000000007000000412e636c6173736d52db4e1351145da72d4c3b0c721750b4de9d2942b1222a37a10594522e52c335c69c0e873230b43a336de27ff8e28bafbe822675a2c6474dfc278dfb1422d430c9cc9cb36f6bedb5f7af3f5fbe03184296814d2a600ccdbbbccce3362fe4e38bb95d617a0a820c2d4bc5a29d2a5a851c7745bf0ca1047a1b33bcbc1f9fdcda7284eb8e509c5e63481b2b0ce7a4256917cd3d73875b05051186485e78296edbc26168d28d9a240d0dd054a868d4500f2582009a181a4e85286861a817af4bdc76193af4ccff9c478c0d0d6d6857d18a0e06c5a158cb110c217dc358d1d0892e09d0cdd05e4ddde7de4e3c69e5670b9ec80b47c1454a2a73bb2416b719baf5b491392b8ea85ec265153d8832b4524bcb629f3ab40af9e98270f26f1882ba91d67015d724dc75c237a96986c15a99ce2ebe994c1f09b32cdc92ed11d84ddc92756e33a827760506e969b9d9926952b12a2675df8b3b2a62e863088f9a3691f2c6ab2e1a084b2ab84b0d12e129ee71a98ab199d4700f83b2fc7dd276f438a14edf4c4ac11ee0a18a041e51725ec3889c4f02a39429fb25638e94d48d5373c87a0ec9409c1f6342c64ed66cd69157418ad8790e2fb8dbc209635ac5941c17131a9e4ac91298952b10566905e6889523cac2f134cc6340125d20fc54718bc6da98f5b8b937cf5f3de7399bee6ab658724c3163d982c40f20444b4e56b94e740a2222ab9265896eaba8abfac77c9cfb88e60acefbb810fb0125f401a1209da391b7ef4f6eb1a88f2bc16f611f377ce80b7d15f493f5b00af08cbe6184da279836419d218e8163902881caa7ed2b12eb3e863e6378bd82b14f685e3dac3a2218c7e071f01ab10bd07ff824782e564132d3fb130a411ff4b6ce54f0e41d3a63b2c84bfaa45facadfac848a78f4576f08f8e86c06f7429482898ea21db32d902a8fb0b504b070805b9dd0d9f020000f9030000504b0304140008080800d4ad282b00000000000000000000000007000000422e636c6173736d545d4f1351103db7bb65b76591adf2dd455a40694bf9100444c040312698a20f3524950759da8a4528a414222f461ff805fc001a124362248104ac11823efbeebf21e2dcedb6dd429beede99b973cfcc9999bb7ffefdbc00308c09061692c018d4657d4bef5bd1534b7d2f179713b18c0481a1762a34f334115b8b27d2bddc81dc17e99911e8f41509312ec824c4196cf32112120c55e3c95432f384c1ee9b0ff9e718049f7f4ec12da84e887029a886e2800d7714d440e2523d439dcf1f2ea510c9a493a9a531c27baba0098d4e726a2e4b32ef21c16dc0478cf773f27fa7a08d07b2c143dab48476273af241ef72eb3db2261574e57d7c0a1c68e552a01c7d7b23935895106470e8e9b4be1d5b5bdf6608f8c2d7cb343653c134c3f9f6a2cf891ef473e6b54e6818a0d04b545362aa6fadf64dc5e3e9c4c60691acb6a812869d18e1be35d36ba98d8c9ecacce92b9b5456719ada40e648468fbd9fd5d75fe98b2ba43b236b9be958e2597225012f1111a923225a5005ea2b4649b34126dd61d139616a8221530b6875d01ea549efc7a4b9c96ea3b53690c3ed40f729ea02c2291a8ec17fd460349a8ea3a6632b0b9ca0ee0b1481af17599a89f001e4c077b484bb8f8c4363f476427089971025aa868d57be08231a303dfc343d0d83aafc6177dc5d50c5854117ae3e7eda1d0f4449b52f5032ee6f46369cc85d13e637ece003796285d9996cbb06b3f35995b74b567b456b5545ab54d12a57b4aa9521d422c6a4414695399bb643b3b63545361162c3689da026788db2f2e371d5b18fea33744473e8fc65807294d722471954abf62c9b39dc3f2cd69e2aecd6486e2a36ef2f05e0d3f2d50890839f854b851bc8c263a99beacd422d15ff005b37427bf7caf3caa76bdf2f4ba87ba2b4a559b6b473f945215c8fa0fdc003035be3d067d0a23d390c5a27a919f64b0cd9b5fa4bb4b02b74c226f1bf66383cc49049f1c89c88dd02c53282ed5682ed591af522413ebe6f6e506caf4851f55888a89e7379b6801b14544f818aeac973198906737864e5520d71486bb844974417dfc63f5f66f6fda4f10968b97ebb8ad18eaded755109f887cd3c1d344f37566e46e9a800d640e2b8719becff01504b070823d52851280300001c060000504b0304140008080800d4ad282b00000000000000000000000007000000432e636c6173733d4ebb0ac240109c358991181f69d3d9a985e9ac44d0a0a0a542fa239e72122fa217f1b7ac040b3fc08f12d7082eec6386d9d97dbd1f4f004304048a5d1021dc8b8b8832a177d1aad0461de4ec9acaa351b976611182c974f167065f31af6e39970e7ec126d591d2ca8c0956b797f8f050f760c32734e25c9f8dd02611592109769c6fb879ebbc38a572ae32890e2aac651b84a8827f42935105359ee86bc5b5c54c5062c0e9dfd1b8fd2ea35d4a9d0f504b0708d8f1b600b0000000d8000000504b01021400140008080800d4ad282b2bc2bf352a000000280000001400040000000000000000000000000000004d4554412d494e462f4d414e49464553542e4d46feca0000504b01021400140008080800d4ad282b05b9dd0d9f020000f9030000070000000000000000000000000070000000412e636c617373504b01021400140008080800d4ad282b23d52851280300001c060000070000000000000000000000000044030000422e636c617373504b01021400140008080800d4ad282bd8f1b600b0000000d80000000700000000000000000000000000a1060000432e636c617373504b05060000000004000400e500000086070000000000000021220000000000000000000000000000000000000000000000000000000000000000");
    }

    @Callable
    public static Address getStakerRegistry() {
        requireNoValue();
        return stakerRegistry;
    }

    /**
     * Registers a pool in the registry.
     *
     * @param signingAddress
     * @param commissionRate the pool commission rate
     * @return the pool coinbase address
     */
    @Callable
    public static void registerPool(Address signingAddress, int commissionRate, byte[] metaDataUrl, byte[] metaDataContentHash) {
        // sanity check
        require(commissionRate >= 0 && commissionRate <= 100);
        requireNoValue();
        requireNonNull(metaDataUrl);
        require(metaDataContentHash != null && metaDataContentHash.length == 32);

        Address caller = Blockchain.getCaller();

        // make sure no one has registered as a staker using this identity
        require(!isStakerRegistered(caller));

        // make sure no one has registered as a pool using this identity
        require(!pools.containsKey(caller));

        Address poolRegistry =  Blockchain.getAddress();

        // step 1: deploy a coinbase contract
        System.arraycopy(poolRegistry.toByteArray(), 0, poolCoinbaseContract, poolCoinbaseContract.length - Address.LENGTH, Address.LENGTH);
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
                .encodeOneAddress(poolRegistry)
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

    private static void delegate(Address delegator, Address pool, BigInteger value, boolean doVote) {
        PoolState ps = pools.get(pool);

        if (doVote) {
            byte[] data;
            if (delegator.equals(pool)) {
                data = new ABIStreamingEncoder()
                        .encodeOneString("bond")
                        .encodeOneAddress(pool)
                        .toBytes();
            } else {
                data = new ABIStreamingEncoder()
                        .encodeOneString("vote")
                        .encodeOneAddress(pool)
                        .toBytes();
            }
            secureCall(stakerRegistry, value, data, Blockchain.getRemainingEnergy());
        }

        BigInteger previousStake = getOrDefault(ps.delegators, delegator, BigInteger.ZERO);
        ps.delegators.put(delegator, previousStake.add(value));

        // update rewards state machine
        ps.rewards.onVote(delegator, Blockchain.getBlockNumber(), value.longValue());

        // possible pool state change
        if (delegator.equals(ps.stakerAddress)) {
            checkPoolState(ps.stakerAddress);
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
    public static long undelegate(Address pool, long amount) {
        requirePool(pool);
        requirePositive(amount);
        requireNoValue();

        detectBlockRewards(pool);

        return undelegate(Blockchain.getCaller(), pool, amount, true);
    }

    private static long undelegate(Address delegator, Address pool, long amount, boolean doUnvote) {
        PoolState ps = pools.get(pool);

        BigInteger previousStake = getOrDefault(ps.delegators, delegator, BigInteger.ZERO);
        BigInteger amountBI = BigInteger.valueOf(amount);
        require(previousStake.compareTo(amountBI) >= 0);
        ps.delegators.put(delegator, previousStake.subtract(amountBI));

        long id = -1;
        if (doUnvote) {
            byte[] data;
            if (delegator.equals(pool)) {
                data = new ABIStreamingEncoder()
                        .encodeOneString("unbondTo")
                        .encodeOneAddress(pool)
                        .encodeOneLong(amount)
                        .encodeOneAddress(delegator)
                        .toBytes();
            } else {
                data = new ABIStreamingEncoder()
                        .encodeOneString("unvoteTo")
                        .encodeOneAddress(pool)
                        .encodeOneLong(amount)
                        .encodeOneAddress(delegator)
                        .toBytes();
            }
            Result result = secureCall(stakerRegistry, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
            id = new ABIDecoder(result.getReturnData()).decodeOneLong();
        }

        // update rewards state machine
        ps.rewards.onUnvote(delegator, Blockchain.getBlockNumber(), amount);

        // possible pool state change
        if (delegator.equals(ps.stakerAddress)) {
            checkPoolState(ps.stakerAddress);
        }

        PoolRegistryEvents.undelegated(id, delegator, pool, amountBI);
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
        long amount = ps.rewards.onWithdraw(caller, Blockchain.getBlockNumber());
        if (caller.equals(ps.stakerAddress)) {
            amount += ps.rewards.onWithdrawOperator();
        }

        if (amount > 0) {
            delegate(caller, pool, BigInteger.valueOf(amount), true);
        }
    }

    private static class StakeTransfer {
        Address initiator;
        Address fromPool;
        Address toPool;
        long amount;

        public StakeTransfer(Address initiator, Address fromPool, Address toPool, long amount) {
            this.initiator = initiator;
            this.fromPool = fromPool;
            this.toPool = toPool;
            this.amount = amount;
        }
    }

    private static Map<Long, StakeTransfer> transfers = new AionMap<>();

    /**
     * Transfers stake from one pool to another pool.
     *
     * @param fromPool the from pool address
     * @param toPool   the to pool address
     * @param amount   the amount of stake to transfer
     * @return the pending transfer id
     */
    @Callable
    public static long transferStake(Address fromPool, Address toPool, long amount) {
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

        BigInteger amountBI = BigInteger.valueOf(amount);
        require(previousStake1.compareTo(amountBI) >= 0);
        ps.delegators.put(caller, previousStake1.subtract(amountBI));

        // update rewards state machine
        ps.rewards.onUnvote(caller, Blockchain.getBlockNumber(), amount);

        byte[] data = new ABIStreamingEncoder()
                    .encodeOneString("transferStakeTo")
                    .encodeOneAddress(fromPool)
                    .encodeOneAddress(toPool)
                    .encodeOneLong(amount)
                    .encodeOneAddress(Blockchain.getAddress())
                    .toBytes();

        Result result = secureCall(stakerRegistry, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
        long id = new ABIDecoder(result.getReturnData()).decodeOneLong();
        transfers.put(id, new StakeTransfer(caller, fromPool, toPool, amount));

        // possible pool state change
        if (caller.equals(ps.stakerAddress)) {
            checkPoolState(ps.stakerAddress);
        }

        PoolRegistryEvents.transferredStake(id, caller, fromPool, toPool, amountBI);
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
    public static long getStake(Address pool, Address delegator) {
        requirePool(pool);
        requireNonNull(delegator);
        requireNoValue();

        return getOrDefault(pools.get(pool).delegators, delegator, BigInteger.ZERO).longValue();
    }

    /**
     * Returns the self-bond stake to a pool.
     *
     * @param pool the pool address
     * @return the amount of stake
     */
    @Callable
    public static long getSelfStake(Address pool) {
        requirePool(pool);
        requireNoValue();

        PoolState ps = pools.get(pool);
        return getOrDefault(ps.delegators, ps.stakerAddress, BigInteger.ZERO).longValue();
    }

    /**
     * Returns the total stake of a pool.
     *
     * @param pool the pool address
     * @return the amount of stake
     */
    @Callable
    public static long getTotalStake(Address pool) {
        requirePool(pool);
        requireNoValue();

        byte[] data = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool)
                .toBytes();
        Result result = secureCall(stakerRegistry, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
        return new ABIDecoder(result.getReturnData()).decodeOneLong();
    }

    /**
     * Finalizes an un-vote operation.
     *
     * @param id pending unvote id
     */
    @Callable
    public static void finalizeUnvote(long id) {
        requireNoValue();

        byte[] data = new ABIStreamingEncoder()
                .encodeOneString("finalizeUnvote")
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

        delegate(transfer.initiator, transfer.toPool, BigInteger.valueOf(transfer.amount), false);
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
        long amount = ps.rewards.onWithdraw(delegator, Blockchain.getBlockNumber());
        if (delegator.equals(ps.stakerAddress)) {
            amount += ps.rewards.onWithdrawOperator();
        }

        Blockchain.println("Auto delegation: rewards = " + amount);

        if (amount > 0) {
            long fee = amount * ps.autoRewardsDelegationDelegators.get(delegator) / 100;
            long remaining = amount - fee;

            Blockchain.println("Auto delegation: fee = " + fee + ", remaining = " + remaining);

            // transfer fee to the caller
            secureCall(Blockchain.getCaller(), BigInteger.valueOf(fee), new byte[0], Blockchain.getRemainingEnergy());

            delegate(delegator, pool, BigInteger.valueOf(remaining), true);
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
    public static long getRewards(Address pool, Address delegator) {
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
    public static long withdraw(Address pool) {
        Address caller = Blockchain.getCaller();
        requirePool(pool);
        requireNoValue();

        detectBlockRewards(pool);

        // query withdraw amount from rewards state machine
        PoolState ps = pools.get(pool);
        long amount = ps.rewards.onWithdraw(caller, Blockchain.getBlockNumber());
        if (caller.equals(ps.stakerAddress)) {
            amount += ps.rewards.onWithdrawOperator();
        }

        BigInteger amountBI = BigInteger.valueOf(amount);
        // do a transfer
        if (amount > 0) {
            secureCall(caller, amountBI, new byte[0], Blockchain.getRemainingEnergy());
        }
        PoolRegistryEvents.withdrew(caller, pool, amountBI);
        return amount;
    }

    @Callable
    public static void updateCommissionRate(Address pool, int newCommissionRate){
        // todo possible to add a delay and max value
        requireNoValue();
        requirePool(pool);
        require(newCommissionRate >= 0 && newCommissionRate <= 100);
        PoolState ps = pools.get(pool);
        require(ps.stakerAddress.equals(Blockchain.getCaller()));
        ps.commissionRate = newCommissionRate;
        ps.rewards.setCommissionRate(newCommissionRate);
        PoolRegistryEvents.updatedCommissionRate(pool, newCommissionRate);
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
        PoolRegistryEvents.updateMetaDataContentHash(pool, newMetaDataContentHash);
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
        return pools.get(pool).isActive ? "ACTIVE" : "BROKEN";
    }

    private static void checkPoolState(Address staker) {
        PoolState ps = pools.get(staker);
        if (ps != null) {
            boolean active = isActive(staker);
            if (ps.isActive && !active) {
                switchToBroken(ps);
            }
            if (!ps.isActive && active) {
                switchToActive(ps);
            }
        }
    }

    private static boolean isActive(Address pool) {
        // TODO: optimize - checking all three condition each time costs too much energy
        return isCoinbaseSetup(pool) && isSelfStakeSatisfied(pool);
    }

    private static boolean isStakerRegistered(Address staker) {
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("isStaker")
                .encodeOneAddress(staker)
                .toBytes();
        Result result = secureCall(stakerRegistry, BigInteger.ZERO, txData, Blockchain.getRemainingEnergy());
        boolean isStaker = new ABIDecoder(result.getReturnData()).decodeOneBoolean();

        return isStaker;
    }

    private static boolean isCoinbaseSetup(Address pool) {
        requirePool(pool);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("getCoinbaseAddressForIdentityAddress")
                .encodeOneAddress(pool)
                .toBytes();
        Result result = secureCall(stakerRegistry, BigInteger.ZERO, txData, Blockchain.getRemainingEnergy());
        Address coinbaseAddress = new ABIDecoder(result.getReturnData()).decodeOneAddress();

        PoolState ps = pools.get(pool);
        return ps.coinbaseAddress.equals(coinbaseAddress);
    }

    private static boolean isSelfStakeSatisfied(Address pool) {
        requirePool(pool);

        PoolState ps = pools.get(pool);
        BigInteger stake = getOrDefault(ps.delegators, ps.stakerAddress, BigInteger.ZERO);

        // can implement a self-bond percentage very easily here
        return stake.compareTo(MIN_SELF_STAKE) >= 0;
    }


    private static void switchToActive(PoolState ps) {
        ps.isActive = true;
        ps.rewards.setCommissionRate(ps.commissionRate);
    }

    private static void switchToBroken(PoolState ps) {
        ps.isActive = false;
        ps.rewards.setCommissionRate(0);

        // alternatively, punishment could be making the staker inactive
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

    private static void requirePositive(long num) {
        require(num > 0);
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
        if (balance.compareTo(BigInteger.ZERO) > 0) {
            byte[] data = new ABIStreamingEncoder()
                    .encodeOneString("transfer")
                    .encodeOneAddress(Blockchain.getAddress())
                    .encodeOneLong(balance.longValue())
                    .toBytes();
            secureCall(ps.coinbaseAddress, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());

            ps.rewards.onBlock(Blockchain.getBlockNumber(), balance.longValue());

            Blockchain.println("New block rewards: " + balance);
        }
    }
}
