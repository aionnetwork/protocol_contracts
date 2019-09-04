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

    // 1000 Aions
    public static final BigInteger MIN_SELF_STAKE = new BigInteger("1000000000000000000000");
    private static final BigInteger MIN_SELF_STAKE_PERCENTAGE = BigInteger.ONE;
    // todo check value
    public static final long COMMISSION_RATE_CHANGE_TIME_LOCK_PERIOD = 6 * 60 * 24 * 7;


    @Initializable
    private static Address stakerRegistry;

    private static byte[] poolCoinbaseContract;

    private static Map<Address, PoolState> pools = new AionMap<>();

    static {
        poolCoinbaseContract = hexStringToByteArray("00000869504b0304140008080800d4ad282b000000000000000000000000140004004d4554412d494e462f4d414e49464553542e4d46feca0000f34dcccb4c4b2d2ed10d4b2d2acecccfb35230d433e0e5f24dccccd375ce492c2eb65270e4e5e2e50200504b07082bc2bf352a00000028000000504b0304140008080800d4ad282b00000000000000000000000007000000412e636c6173738d525d53d340143ddb16d286f0fd218a8a9fd80fa45811c556841450a0085206067860b661098134d52465c69fe29b6fbe82ced48e3a3eea8cff49c7bb05ad303ef890cdeeddb3e7de73eefdfef3e31700a3586160930a1843db1e3fe0499b3b6672b1b0270c5f4190a17da954b2b325cb29704f0c49083da0af39c70f8ac9c9ed6d57785e9a61207a2a90ab9115b9bf9bd42d73d6f18529dc746c95a145c274bb64ec1bbbdc7214441822a6f0b3dcb685cbd01a8d9d62d2d0044d858a660d8d502208a095a1e92f8882768646f1b2cc6d8fa13b9a3b2b241ddbd0d0892e151de866505cc25aae6008453762ab1acea1572638cfd0f5afb215f411bf67994eb9c8108cc666355cc2651517d1cfd041b52f8b2249b11c73da11aef9aa069ad37015d724ef754a64903a8691ff3169539f3b7660597865db27036e6240f2dc6250eb71053132cef2f265c320b25a4e9299c0a08a386e338433864d45f9e3b52b729ee90aee907c2a788afb5cca8f6dea1aee6244d2df23919993070dd14d5d3a731f0f54a430468f8586b46c440a197a29f552b0409651bfea86e77d976ca09a1f634262274fcdd5f1ad822c55e7bbdcf176841bc6b48a29d917b6c3d0fb9bedac2b1a9e625632cec93108ab3406392ad81507c2f5353cc3b0d4b048a5654bdbd4dae6bccf8dfd05fe6285176c3aabf952d935c48c650bea4b00219a7e8aca91a25d1011c94a91e7745a450345804c152defd056414f1517e25fa184de2214a47da2822ba137f5733c11fc1caee24615d185c10a86287454e35fa6358c50d7046b9920cd4862f824477f2d03d0f909a9f52a463fe0e17a058fdea36deda87611c138464ec06b040ed07fac0e9e8f57a0e712dfa050eac344c74c054f5ea3272e49b66899df5aab6241de55b1c40eff54a321f003bd0a520aa6fa2896a758000dbf00504b0708db58d443a102000010040000504b0304140008080800d4ad282b00000000000000000000000007000000422e636c6173739d545d4f1341143dd3ddb2edb6c8a2503e5ab41484520a45101001138a316952f40143527990a55d4bb1b4a42c445e8cf12ff003242486c4f80089502304df7df707f83f1af1ceb26d17a889b1e9eedc7be7de33f7cc99d91fbfbf9d0118c334038b4a600ccaaabaa546b26a2e1d79b6bcaa2575090243c34c34f6584be6535a61902750fa323d3181aacfc94872c341468ac1b618254363a89bcae432fa23067b7031dab7c02004fb16dcb80145868846375c703b61c32d37ea2171ab99a129d817afb630af1732b9f4244f6d9129a1f5528317b312da0de8795a35ed460787b7e136792b6ef82fbc4ef2662574c9e8e60b3be1e3d19ecb68db1bbab62621c8e0540b05753b995fdf660805e357b76432562314e3dc42e897d1873067d920c38b41da3b62a46ead456652a982b6b131c9e0b2b812eec918e679ec15436b99fd9aaaaf44a299742ca76b69ad40454db5e212c6648cf3eafad97c6e435773fa829adda4cd1767492c0acfeb6af2f59cbafe5c5dce922fcfe7370b49ed4926aba193b64024dd44b4a30ea43e26c8b3c141bed3e2f3ad22a90c9b84e24ad31c11a4f743f2bc14b7d1d8102ae266a8ff084d21e1089e43f01f1d03b49889136662070b7d41d347b8053e9eedd2c989efc3113a465bbcffc0289aa4b70ca1512c41948c1548b30a8c68c00cf06a7a3c238ae3cdce94b7ec8a4b238d387ffb6e672a9420d7be44cd783f9bddb82addfc84dda0ff89dabe434f80c5ab80c3bbf05bf094ce5d2855b47d6c19933cf042e411caf800d709ba1345dcfd6ed0e2a529fb9e255a44ef7475ca6799f29d3a9e96971b107c5f891bc7f671e8137813034544ac5bd3067b09a3765f73890eff391d6a9bc4ff5e23a11e4326c503a2c86fe64e99e22582012bc1c02e695721c8f578798d62a02645c56f21a2f84f1d7365dcb0a0f8cb5414ff0597e144b888112b1717c4519fa7845e89ee01c37d8c9addff32053afe1781baae08f4fe5af75d35bb17f72e47ad25ff25d63817ebc15fc4eab188356e5c28fa5c997487c86334b65fbd1d95850f2b9074011a09847fdecceab059dd52fbec554b05300f9953c6eaf63f504b07082a6a89950e03000002060000504b0304140008080800d4ad282b00000000000000000000000007000000432e636c6173733d4ebb0ac240109c358991181f69d3d9a985e9ac44d0a0a0a542fa239e72122fa217f1b7ac040b3fc08f12d7082eec6386d9d97dbd1f4f004304048a5d1021dc8b8b8832a177d1aad0461de4ec9acaa351b976611182c974f167065f31af6e39970e7ec126d591d2ca8c0956b797f8f050f760c32734e25c9f8dd02611592109769c6fb879ebbc38a572ae32890e2aac651b84a8827f42935105359ee86bc5b5c54c5062c0e9dfd1b8fd2ea35d4a9d0f504b0708d8f1b600b0000000d8000000504b01021400140008080800d4ad282b2bc2bf352a000000280000001400040000000000000000000000000000004d4554412d494e462f4d414e49464553542e4d46feca0000504b01021400140008080800d4ad282bdb58d443a102000010040000070000000000000000000000000070000000412e636c617373504b01021400140008080800d4ad282b2a6a89950e03000002060000070000000000000000000000000046030000422e636c617373504b01021400140008080800d4ad282bd8f1b600b0000000d8000000070000000000000000000000000089060000432e636c617373504b05060000000004000400e50000006e070000000000000021220000000000000000000000000000000000000000000000000000000000000000");
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

        return undelegate(Blockchain.getCaller(), pool, amount, true);
    }

    private static long undelegate(Address delegator, Address pool, BigInteger amount, boolean doUndelegate) {
        PoolState ps = pools.get(pool);

        BigInteger previousStake = getOrDefault(ps.delegators, delegator, BigInteger.ZERO);

        require(previousStake.compareTo(amount) >= 0);
        ps.delegators.put(delegator, previousStake.subtract(amount));

        long id = -1;
        if (doUndelegate) {
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
            id = new ABIDecoder(result.getReturnData()).decodeOneLong();
        }

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
        require(newCommissionRate >= 0 && newCommissionRate <= 100);
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
        return isSelfStakeSatisfied(pool) ? "ACTIVE" : "BROKEN";
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
        // balance > 0
        if (balance.signum() == 1) {
            byte[] data = new ABIStreamingEncoder()
                    .encodeOneString("transfer")
                    .encodeOneAddress(Blockchain.getAddress())
                    .encodeOneBigInteger(balance)
                    .toBytes();
            secureCall(ps.coinbaseAddress, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());

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
