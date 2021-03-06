package org.aion.unity;

import avm.Address;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.core.util.LogSizeUtils;
import org.aion.avm.embed.AvmRule;
import org.aion.avm.tooling.ABIUtil;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;
import org.aion.kernel.TestingState;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Scanner;

import static org.junit.Assert.*;

public class PoolRegistryTest {

    private static BigInteger ENOUGH_BALANCE_TO_TRANSACT = BigInteger.TEN.pow(18 + 5);
    private static BigInteger MIN_SELF_STAKE = new BigInteger("1000000000000000000000");
    private static long COMMISSION_RATE_CHANGE_TIME_LOCK_PERIOD = 6 * 60 * 24 * 7;
    private static long UNBOND_LOCK_UP_PERIOD = 6 * 60 * 24;
    
    @Rule
    public AvmRule RULE = new AvmRule(false);

    // default address with balance
    private Address preminedAddress = RULE.getPreminedAccount();

    // contract address
    private Address stakerRegistry;
    private Address poolRegistry;

    @Before
    public void setup() {
        try (Scanner s = new Scanner(PoolRegistryTest.class.getResourceAsStream("StakerRegistry.txt"))) {
            String contract = s.nextLine();
            AvmRule.ResultWrapper result = RULE.deploy(preminedAddress, BigInteger.ZERO, Hex.decode(contract));
            assertTrue(result.getReceiptStatus().isSuccess());
            stakerRegistry = result.getDappAddress();
        }

        Address placeHolder = new Address(Helpers.hexStringToBytes("0000000000000000000000000000000000000000000000000000000000000000"));
        byte[] coinbaseArguments = ABIUtil.encodeDeploymentArguments(placeHolder);
        byte[] coinbaseBytes = RULE.getDappBytes(PoolCoinbase.class, coinbaseArguments, 1);

        byte[] arguments = ABIUtil.encodeDeploymentArguments(stakerRegistry, MIN_SELF_STAKE, BigInteger.ONE, COMMISSION_RATE_CHANGE_TIME_LOCK_PERIOD, coinbaseBytes);
        byte[] data = RULE.getDappBytes(PoolRegistry.class, arguments, 1, PoolStorageObjects.class, PoolRewardsStateMachine.class, PoolRegistryEvents.class, PoolRegistryStorage.class);

        AvmRule.ResultWrapper result = RULE.deploy(preminedAddress, BigInteger.ZERO, data);
        assertTrue(result.getReceiptStatus().isSuccess());

        assertEquals(1, result.getLogs().size());
        Log poolRegistryEvent = result.getLogs().get(0);
        assertArrayEquals(LogSizeUtils.truncatePadTopic("ADSDeployed".getBytes()), poolRegistryEvent.copyOfTopics().get(0));
        assertArrayEquals(stakerRegistry.toByteArray(), poolRegistryEvent.copyOfTopics().get(1));
        assertEquals(MIN_SELF_STAKE, new BigInteger(poolRegistryEvent.copyOfTopics().get(2)));
        assertEquals(BigInteger.ONE, new BigInteger(poolRegistryEvent.copyOfTopics().get(3)));
        assertEquals(COMMISSION_RATE_CHANGE_TIME_LOCK_PERIOD, new BigInteger(poolRegistryEvent.copyOfData()).longValue());

        poolRegistry = result.getDappAddress();
    }

    public Address setupNewPool(int fee) {
        fee = fee * 10000;
        Address newPool = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        // register a new pool
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("registerPool")
                .encodeOneAddress(newPool)
                .encodeOneInteger(fee)
                .encodeOneByteArray("https://".getBytes())
                .encodeOneByteArray(new byte[32])
                .toBytes();

        AvmRule.ResultWrapper result = RULE.call(newPool, poolRegistry, nStake(1), txData, 2_000_000L, 1L);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // verify now
        txData = new ABIStreamingEncoder()
                .encodeOneString("isActive")
                .encodeOneAddress(newPool)
                .toBytes();
        result = RULE.call(newPool, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(true, result.getDecodedReturnData());

        return newPool;
    }

    @Test
    public void testPoolWorkflow() {
        setupNewPool(10);
    }

    @Test
    public void testGetStakerRegistry() {
        byte[] txData = ABIUtil.encodeMethodArguments("getStakerRegistry");
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);

        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(stakerRegistry, result.getDecodedReturnData());
    }

    @Test
    public void testRegister() {
        setupNewPool(10);
    }

    @Test
    public void testDelegate() {
        Address pool = setupNewPool(10);
        BigInteger stake = BigInteger.TEN;

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, poolRegistry, stake, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool)
                .encodeOneAddress(preminedAddress)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(stake, result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        assertEquals(stake.add(nStake(1)), result.getDecodedReturnData());
        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(stake.add(nStake(1)), ((BigInteger[]) result.getDecodedReturnData())[0]);
    }

    @Test
    public void testUndelegate() {
        Address pool = setupNewPool(10);

        BigInteger stake = BigInteger.TEN;
        BigInteger unstake = BigInteger.ONE;

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, poolRegistry, stake, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneBigInteger(unstake)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long id = (long) result.getDecodedReturnData();

        assertEquals(2, result.getLogs().size());
        Log poolRegistryEvent = result.getLogs().get(1);
        assertArrayEquals(LogSizeUtils.truncatePadTopic("ADSUndelegated".getBytes()),
                poolRegistryEvent.copyOfTopics().get(0));
        assertEquals(id, new BigInteger(poolRegistryEvent.copyOfTopics().get(1)).longValue());
        assertArrayEquals(preminedAddress.toByteArray(), poolRegistryEvent.copyOfTopics().get(2));
        assertArrayEquals(pool.toByteArray(), poolRegistryEvent.copyOfTopics().get(3));
        ABIDecoder decoder = new ABIDecoder(poolRegistryEvent.copyOfData());
        assertEquals(unstake, decoder.decodeOneBigInteger());
        assertEquals(BigInteger.ZERO, decoder.decodeOneBigInteger());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool)
                .encodeOneAddress(preminedAddress)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(stake.longValue() - unstake.longValue(), ((BigInteger) result.getDecodedReturnData()).longValue());

        tweakBlockNumber(getBlockNumber() +  UNBOND_LOCK_UP_PERIOD);

        // release the pending undelegate
        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeUndelegate")
                .encodeOneLong(id)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
    }

    @Test
    public void testUndelegateFromBrokenPool() {
        Address pool = setupNewPool(10);

        BigInteger stake = BigInteger.TEN;
        BigInteger unstake = BigInteger.ONE;

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, poolRegistry, stake, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // get the amount we self delegated, so know how much to undelegate
        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool)
                .encodeOneAddress(pool)
                .toBytes();

        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        BigInteger selfDelegated = (BigInteger) result.getDecodedReturnData();

        // now remove self delegation to put pool into broken state
        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneBigInteger(selfDelegated)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long selfUndelegateFinalizationId = (long) result.getDecodedReturnData();

        // verify state of the pool is broken
        txData = new ABIStreamingEncoder()
                .encodeOneString("isActive")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        assertFalse((Boolean) result.getDecodedReturnData());

        // now attempt (like before) to undelegate
        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneBigInteger(unstake)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long id = (long) result.getDecodedReturnData();

        assertEquals(2, result.getLogs().size());
        Log poolRegistryEvent = result.getLogs().get(1);
        assertArrayEquals(LogSizeUtils.truncatePadTopic("ADSUndelegated".getBytes()),
                poolRegistryEvent.copyOfTopics().get(0));
        assertEquals(id, new BigInteger(poolRegistryEvent.copyOfTopics().get(1)).longValue());
        assertArrayEquals(preminedAddress.toByteArray(), poolRegistryEvent.copyOfTopics().get(2));
        assertArrayEquals(pool.toByteArray(), poolRegistryEvent.copyOfTopics().get(3));
        ABIDecoder decoder = new ABIDecoder(poolRegistryEvent.copyOfData());
        assertEquals(unstake, decoder.decodeOneBigInteger());
        assertEquals(BigInteger.ZERO, decoder.decodeOneBigInteger());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool)
                .encodeOneAddress(preminedAddress)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(stake.longValue() - unstake.longValue(), ((BigInteger) result.getDecodedReturnData()).longValue());

        tweakBlockNumber(getBlockNumber() +  UNBOND_LOCK_UP_PERIOD);

        // release the pending undelegate from pool
        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeUndelegate")
                .encodeOneLong(selfUndelegateFinalizationId)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // release the pending undelegate
        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeUndelegate")
                .encodeOneLong(id)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
    }

    @Test
    public void testTransferStake() {
        Address pool1 = setupNewPool(10);
        Address pool2 = setupNewPool(10);
        Address delegator = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        // delegate 2 stake
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool1)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, BigInteger.TWO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // transfer 1 stake
        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(pool1)
                .encodeOneAddress(pool2)
                .encodeOneBigInteger(BigInteger.ONE)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        long id = (long) result.getDecodedReturnData();
        assertTrue(result.getReceiptStatus().isSuccess());

        // bump block number and finalize the transfer
        tweakBlockNumber(RULE.kernel.getBlockNumber() + 6 * 10);
        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeTransfer")
                .encodeOneLong(id)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // now, query the stake of the pool1 and pool2 from the staker registry
        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool1)
                .toBytes();
        result = RULE.call(delegator, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        BigInteger stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(nStake(1).add(BigInteger.ONE), stake);

        // and from the pool registry
        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool1)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = ((BigInteger[]) result.getDecodedReturnData())[0];
        assertEquals(nStake(1).add(BigInteger.ONE), stake);

        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool2)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = ((BigInteger[]) result.getDecodedReturnData())[0];
        assertEquals(nStake(1).add(BigInteger.ONE), stake);

        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool1)
                .encodeOneAddress(delegator)
                .encodeOneAddress(poolRegistry)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(BigInteger.ONE, result.getDecodedReturnData());
    }

    @Test
    public void testUsecaseAutoRedelegate() {
        Address delegator = RULE.getRandomAddress(nStake(10));
        Address pool = setupNewPool(4);

        // delegate stake
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, nStake(1), txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // enable auto delegation with 20% fee
        txData = new ABIStreamingEncoder()
                .encodeOneString("enableAutoRewardsDelegation")
                .encodeOneAddress(pool)
                .encodeOneInteger(200000)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // produce a block
        generateBlock(pool, 100);

        // some third party calls autoDelegateRewards
        Address random = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);
        txData = new ABIStreamingEncoder()
                .encodeOneString("autoDelegateRewards")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(random, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // query the stake of the delegator
        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long reward = (100 - 4) / 2;

        assertEquals(nStake(1).add(BigInteger.valueOf(reward - reward * 20 / 100)), result.getDecodedReturnData());
    }

    @Test
    public void testUsecaseWithdraw() {
        Address pool = setupNewPool(4);
        Address user1 = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);
        Address user2 = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);


        // User1 delegate 1 stake to the pool
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(user1, poolRegistry, nStake(1), txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // User2 delegate 1 stake to the pool
        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(user2, poolRegistry, nStake(1), txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // The pool generates one block
        generateBlock(pool, 9);

        // User1 withdraw
        txData = new ABIStreamingEncoder()
                .encodeOneString("withdrawRewards")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(user1, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        BigInteger amount = (BigInteger) result.getDecodedReturnData();
        assertEquals(3, amount.longValue());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getRewards")
                .encodeOneAddress(pool)
                .encodeOneAddress(user1)
                .toBytes();
        result = RULE.call(user1, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        amount = (BigInteger) result.getDecodedReturnData();
        assertEquals(0, amount.longValue());
    }

    @Test
    public void testUsecaseRedelegate() {
        Address pool = setupNewPool(10);
        Address delegator = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        // Delegator stake 1
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, nStake(1), txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // The pool generates one block
        generateBlock(pool, 1000);

        // User1 redelegate
        txData = new ABIStreamingEncoder()
                .encodeOneString("redelegateRewards")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // The pool generates another block
        generateBlock(pool, 1000);

        // User1 withdraw
        txData = new ABIStreamingEncoder()
                .encodeOneString("withdrawRewards")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        BigInteger amount = (BigInteger) result.getDecodedReturnData();
        assertEquals(449, amount.longValue());

        // Check the stake owned by pool registry
        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        BigInteger stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(3875820019684213186L, stake.longValue());

        txData = new ABIStreamingEncoder()
                .encodeOneString("redelegateRewards")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(0, result.getTransactionResult().logs.size());
    }

    @Test
    public void testSelfStake() {
        Address pool = setupNewPool(4);
        Address delegator = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        // delegator stake 1 wei
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, BigInteger.ONE, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // pool stake 1 wei
        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ONE, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // query the total stake of the pool
        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        BigInteger stake = ((BigInteger[]) result.getDecodedReturnData())[0];
        assertEquals(nStake(1).add(BigInteger.TWO), stake);

        // query the self stake of the pool
        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool)
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(nStake(1).add(BigInteger.ONE), stake);
    }

    @Test
    public void delegateAndTransferSelfStake(){
        Address pool1 = setupNewPool(4);
        Address pool2 = setupNewPool(4);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool1)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(pool1, poolRegistry, BigInteger.TEN, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(pool1)
                .encodeOneAddress(pool2)
                .encodeOneBigInteger(BigInteger.TEN)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(pool1, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isFailed());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool2)
                .toBytes();
        result = RULE.call(pool1, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        BigInteger stake = ((BigInteger[]) result.getDecodedReturnData())[0];
        assertEquals(nStake(1), stake);
    }

    @Test
    public void transferStakeToOtherPoolOwner(){
        Address delegator = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        Address pool1 = setupNewPool(4);
        Address pool2 = setupNewPool(4);

        // delegator stake 1 wei
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool1)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(pool2, poolRegistry, BigInteger.TEN, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(pool1)
                .encodeOneAddress(pool2)
                .encodeOneBigInteger(BigInteger.TEN)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(pool2, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isFailed());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool2)
                .encodeOneAddress(pool2)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        BigInteger stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(nStake(1), stake);
    }

    @Test
    public void testCommissionRateUpdate() {
        Address pool = setupNewPool(10);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("requestCommissionRateChange")
                .encodeOneInteger(20)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        // validate the log
        assertEquals(1, result.getLogs().size());
        assertArrayEquals(LogSizeUtils.truncatePadTopic("ADSCommissionRateChangeRequested".getBytes()),
                result.getLogs().get(0).copyOfTopics().get(0));
        assertEquals(BigInteger.ZERO, new BigInteger(result.getLogs().get(0).copyOfTopics().get(1)));
        assertArrayEquals(pool.toByteArray(), result.getLogs().get(0).copyOfTopics().get(2));
        assertArrayEquals(BigInteger.valueOf(20).toByteArray(), result.getLogs().get(0).copyOfData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getPoolInfo")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        byte[] info = (byte[]) result.getDecodedReturnData();
        //100000
        byte[] expected = new byte[]{0, 1, -122, -96};
        Assert.assertArrayEquals(expected, Arrays.copyOfRange(info, 32 + 1 + 1, 32 + 1 + 1 + 4));

        tweakBlockNumber(getBlockNumber() + COMMISSION_RATE_CHANGE_TIME_LOCK_PERIOD);

        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeCommissionRateChange")
                .encodeOneLong(0)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // validate the log
        assertEquals(1, result.getLogs().size());
        assertArrayEquals(LogSizeUtils.truncatePadTopic("ADSCommissionRateChangeFinalized".getBytes()),
                result.getLogs().get(0).copyOfTopics().get(0));
        assertArrayEquals(BigInteger.ZERO.toByteArray(), result.getLogs().get(0).copyOfData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getPoolInfo")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        info = (byte[]) result.getDecodedReturnData();
        expected = new byte[]{0, 0, 0, 20};
        Assert.assertArrayEquals(expected, Arrays.copyOfRange(info, 32 + 1 + 1, 32 + 1 + 1 + 4));

        txData = new ABIStreamingEncoder()
                .encodeOneString("requestCommissionRateChange")
                .encodeOneInteger(30)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isFailed());
    }

    @Test
    public void testMetaDataUpdate() {
        Address pool = setupNewPool(10);
        byte[] newMetaDataUrl = "http://".getBytes();
        byte[] newMetaDataContentHash = new byte[32];
        Arrays.fill(newMetaDataContentHash, Byte.MIN_VALUE);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("updateMetaData")
                .encodeOneByteArray(newMetaDataUrl)
                .encodeOneByteArray(newMetaDataContentHash)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        // validate the log
        assertEquals(1, result.getLogs().size());
        assertArrayEquals(LogSizeUtils.truncatePadTopic("ADSPoolMetaDataUpdated".getBytes()),
                result.getLogs().get(0).copyOfTopics().get(0));
        assertArrayEquals(pool.toByteArray(), result.getLogs().get(0).copyOfTopics().get(1));
        assertArrayEquals(newMetaDataContentHash, result.getLogs().get(0).copyOfTopics().get(2));
        assertArrayEquals(newMetaDataUrl, result.getLogs().get(0).copyOfData());
    }

    @Test
    public void testGetPoolInfo(){
        Address pool = setupNewPool(10);
        byte[] metaDataUrl = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes();
        byte[] newMetaDataContentHash = new byte[32];
        Arrays.fill(newMetaDataContentHash, Byte.MIN_VALUE);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("updateMetaData")
                .encodeOneByteArray(metaDataUrl)
                .encodeOneByteArray(newMetaDataContentHash)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getPoolInfo")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        byte[] info = (byte[]) result.getDecodedReturnData();
        ABIDecoder decoder = new ABIDecoder(info);
        Assert.assertEquals(getCoinbaseAddress(pool), decoder.decodeOneAddress());
        Assert.assertEquals(10 * 10000, decoder.decodeOneInteger());
        Assert.assertEquals(true, decoder.decodeOneBoolean());
        Assert.assertArrayEquals(newMetaDataContentHash, decoder.decodeOneByteArray());
        Assert.assertArrayEquals(metaDataUrl, decoder.decodeOneByteArray());
    }

    @Test
    public void testSelfStakeConditions() {
        Address pool = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("registerPool")
                .encodeOneAddress(pool)
                .encodeOneInteger(4)
                .encodeOneByteArray("https://".getBytes())
                .encodeOneByteArray(new byte[32])
                .toBytes();

        AvmRule.ResultWrapper result = RULE.call(pool, poolRegistry, MIN_SELF_STAKE, txData, 2_000_000L, 1L);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        Address delegator = RULE.getRandomAddress(nStake(105));

        txData = new ABIStreamingEncoder()
                .encodeOneString("isActive")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(true, result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ONE, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // delegation would put the pool into a broken state, so it's reverted
        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, nStake(100), txData);
        assertTrue(result.getReceiptStatus().isFailed());

        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneBigInteger(nStake(1))
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("isActive")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(false, result.getDecodedReturnData());
    }

    @Test
    public void testGetOutstandingRewards() {
        Address pool = setupNewPool(4);

        BigInteger rewards = BigInteger.valueOf(1000);
        generateBlock(pool, rewards.longValue());

        AionAddress coinbaseAddress = new AionAddress((getCoinbaseAddress(pool).toByteArray()));
        assertEquals(rewards, RULE.kernel.getBalance(coinbaseAddress));

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(pool, poolRegistry, BigInteger.TEN, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getOutstandingRewards")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(rewards, result.getDecodedReturnData());

        rewards = RULE.kernel.getBalance(coinbaseAddress);
        assertEquals(BigInteger.ZERO, rewards);
    }

    @Test
    public void testUndelegateWithdraw(){
        Address pool = setupNewPool(4);
        Address delegator = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, nStake(1), txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        generateBlock(pool, 1000);

        long expectedDelegatorRewards = (1000 - 40) / 2;
        txData = new ABIStreamingEncoder()
                .encodeOneString("withdrawRewards")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.valueOf(expectedDelegatorRewards), result.getDecodedReturnData());

        generateBlock(pool, 1000);

        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneBigInteger(nStake(1))
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long id = (long) result.getDecodedReturnData();

        tweakBlockNumber(getBlockNumber() +  UNBOND_LOCK_UP_PERIOD);

        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeUndelegate")
                .encodeOneLong(id)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getRewards")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.valueOf(expectedDelegatorRewards), result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("withdrawRewards")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.valueOf(expectedDelegatorRewards), result.getDecodedReturnData());

        long expectedPoolRewards = (1000 - 40) / 2 + (1000 - 40) / 2 + 40 + 40;
        txData = new ABIStreamingEncoder()
                .encodeOneString("withdrawRewards")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.valueOf(expectedPoolRewards), result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getRewards")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.ZERO, result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("withdrawRewards")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.ZERO, result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("withdrawRewards")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.ZERO, result.getDecodedReturnData());
    }

    @Test
    public void testDelegateUndelegateMultipleTimes(){
        Address pool = setupNewPool(4);
        Address delegator = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, nStake(1), txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(nStake(1), result.getDecodedReturnData());

        // User2 delegate 1 stake to the pool
        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, nStake(1), txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(nStake(1).multiply(BigInteger.TWO), result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneBigInteger(nStake(1))
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long id1 = (long) result.getDecodedReturnData();

        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneBigInteger(BigInteger.TEN)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long id2 = (long) result.getDecodedReturnData();

        tweakBlockNumber(getBlockNumber() + UNBOND_LOCK_UP_PERIOD);

        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeUndelegate")
                .encodeOneLong(id1)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        // NOTE: it includes the non-finalized undelegation
        Assert.assertEquals(nStake(1).subtract(BigInteger.TEN), result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeUndelegate")
                .encodeOneLong(id2)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(nStake(1).subtract(BigInteger.TEN), result.getDecodedReturnData());
    }
    @Test
    public void testNonExistentCases(){
        Address pool = setupNewPool(4);
        Address delegator = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.ZERO, result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(delegator)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isFailed());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getRewards")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.ZERO, result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getAutoRewardsDelegationFee")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(-1, result.getDecodedReturnData());
    }

    @Test
    public void testUndelegateFee() {
        Address pool = setupNewPool(10);
        Address delegator = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        BigInteger stake = nStake(2);
        BigInteger unstake = nStake(1);
        BigInteger fee = BigInteger.TEN.pow(10);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, stake, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneBigInteger(unstake)
                .encodeOneBigInteger(unstake.add(BigInteger.TEN))
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isFailed());

        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneBigInteger(unstake)
                .encodeOneBigInteger(fee)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long id = (long) result.getDecodedReturnData();

        BigInteger delegatorBalance = RULE.kernel.getBalance(new AionAddress(delegator.toByteArray()));

        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(stake.longValue() - unstake.longValue(), ((BigInteger) result.getDecodedReturnData()).longValue());

        tweakBlockNumber(getBlockNumber() + UNBOND_LOCK_UP_PERIOD);

        BigInteger preminedBalance = RULE.kernel.getBalance(new AionAddress(preminedAddress.toByteArray()));

        // release the pending undelegate
        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeUndelegate")
                .encodeOneLong(id)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        Assert.assertEquals(RULE.kernel.getBalance(new AionAddress(delegator.toByteArray())), delegatorBalance.add(unstake).subtract(fee));
        Assert.assertEquals(RULE.kernel.getBalance(new AionAddress(preminedAddress.toByteArray())),
                preminedBalance.add(fee).subtract(BigInteger.valueOf(result.getTransactionResult().energyUsed)));
    }

    @Test
    public void testUndelegateAllFee() {
        Address pool = setupNewPool(10);
        Address delegator = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        BigInteger stake = nStake(2);
        BigInteger unstake = nStake(1);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, stake, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneBigInteger(unstake)
                .encodeOneBigInteger(unstake.add(BigInteger.TEN))
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isFailed());

        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneBigInteger(unstake)
                .encodeOneBigInteger(unstake)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long id = (long) result.getDecodedReturnData();

        BigInteger delegatorBalance = RULE.kernel.getBalance(new AionAddress(delegator.toByteArray()));
        BigInteger delegatorStake = stake.subtract(unstake);

        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(delegatorStake, result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(delegatorStake.add(nStake(1)), result.getDecodedReturnData());

        tweakBlockNumber(getBlockNumber() + UNBOND_LOCK_UP_PERIOD);

        BigInteger preminedBalance = RULE.kernel.getBalance(new AionAddress(preminedAddress.toByteArray()));

        // release the pending undelegate
        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeUndelegate")
                .encodeOneLong(id)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        Assert.assertEquals(RULE.kernel.getBalance(new AionAddress(delegator.toByteArray())), delegatorBalance);
        Assert.assertEquals(RULE.kernel.getBalance(new AionAddress(preminedAddress.toByteArray())),
                preminedBalance.add(unstake).subtract(BigInteger.valueOf(result.getTransactionResult().energyUsed)));
    }

    @Test
    public void testTransferFee() {
        Address pool1 = setupNewPool(10);
        Address pool2 = setupNewPool(5);
        Address delegator = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        BigInteger stake = nStake(2);
        BigInteger unstake = nStake(1);
        BigInteger fee = BigInteger.TEN.pow(10);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool1)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, stake, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(pool1)
                .encodeOneAddress(pool2)
                .encodeOneBigInteger(unstake)
                .encodeOneBigInteger(unstake.add(BigInteger.TEN))
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isFailed());

        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(pool1)
                .encodeOneAddress(pool2)
                .encodeOneBigInteger(unstake)
                .encodeOneBigInteger(unstake)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isFailed());

        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(pool1)
                .encodeOneAddress(pool2)
                .encodeOneBigInteger(unstake)
                .encodeOneBigInteger(fee)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long id = (long) result.getDecodedReturnData();

        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool2)
                .toBytes();
        result = RULE.call(pool1, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        BigInteger pendingStake = ((BigInteger[]) result.getDecodedReturnData())[1];
        assertEquals(unstake.subtract(fee), pendingStake);

        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool1)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(stake.longValue() - unstake.longValue(), ((BigInteger) result.getDecodedReturnData()).longValue());

        tweakBlockNumber(getBlockNumber() + 6 * 10);

        BigInteger preminedBalance = RULE.kernel.getBalance(new AionAddress(preminedAddress.toByteArray()));

        // release the pending undelegate
        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeTransfer")
                .encodeOneLong(id)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        Assert.assertEquals(RULE.kernel.getBalance(new AionAddress(preminedAddress.toByteArray())),
                preminedBalance.add(fee).subtract(BigInteger.valueOf(result.getTransactionResult().energyUsed)));

        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool2)
                .toBytes();
        result = RULE.call(pool1, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        pendingStake = ((BigInteger[]) result.getDecodedReturnData())[1];
        assertEquals(BigInteger.ZERO, pendingStake);

        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool2)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(unstake.subtract(fee).longValue(), ((BigInteger) result.getDecodedReturnData()).longValue());
    }

    @Test
    public void testSetSigningAddress(){
        Address pool1 = setupNewPool(10);
        tweakBlockNumber(getBlockNumber() +  6 * 60 * 24 * 7);

        Address newSigningAddress = RULE.getRandomAddress(BigInteger.ZERO);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("setSigningAddress")
                .encodeOneAddress(newSigningAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(pool1, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
    }

    @Test
    public void testRegisterPool(){
        Address newPool = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("registerPool")
                .encodeOneAddress(newPool)
                .encodeOneInteger(10)
                .encodeOneByteArray("https://".getBytes())
                .encodeOneByteArray(new byte[32])
                .toBytes();

        AvmRule.ResultWrapper result = RULE.call(newPool, poolRegistry, nStake(1).divide(BigInteger.TWO), txData, 2_000_000L, 1L);
        Assert.assertTrue(result.getReceiptStatus().isFailed());

        txData = new ABIStreamingEncoder()
                .encodeOneString("registerPool")
                .encodeOneAddress(newPool)
                .encodeOneInteger(10)
                .encodeOneByteArray("https://".getBytes())
                .encodeOneByteArray(new byte[32])
                .toBytes();

        result = RULE.call(newPool, poolRegistry, nStake(1).multiply(BigInteger.TWO), txData, 2_000_000L, 1L);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
    }

    @Test
    public void testChangeCommissionRateInBrokenState() {
        Address pool = setupNewPool(4);

        Address delegator = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);
        BigInteger stake = nStake(1);
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, stake, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        generateBlock(pool, 100000);

        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneBigInteger(nStake(1))
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        generateBlock(pool, 100000);

        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneBigInteger(nStake(1))
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // this cannot happen for normal pool operation because pool is in a broken state. However, someone else can use this coinbase address as their mining or staking coinbase address
        generateBlock(pool, 100000);

        txData = new ABIStreamingEncoder()
                .encodeOneString("requestCommissionRateChange")
                .encodeOneInteger(20)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        tweakBlockNumber(getBlockNumber() + COMMISSION_RATE_CHANGE_TIME_LOCK_PERIOD);

        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeCommissionRateChange")
                .encodeOneLong(0)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
    }

    @Test
    public void testFailedAutoRedelegateRewards() {
        Address pool = setupNewPool(10);
        Address delegator = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        BigInteger stake = nStake(2);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, stake, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // some third party calls autoDelegateRewards
        Address random = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);
        txData = new ABIStreamingEncoder()
                .encodeOneString("autoDelegateRewards")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(random, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isFailed());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getAutoRewardsDelegationFee")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(-1, result.getDecodedReturnData());
    }

    @Test
    public void testFallback(){
        Assert.assertTrue(RULE.balanceTransfer(preminedAddress, poolRegistry, BigInteger.TEN, 50000L, 1L).getReceiptStatus().isFailed());
    }

    /**
     * N unit of MIN_SELF_STAKE.
     *
     * @param n
     * @return
     */
    private BigInteger nStake(int n) {
        return MIN_SELF_STAKE.multiply(BigInteger.valueOf(n));
    }

    private void generateBlock(Address pool, long blockRewards) {
        AionAddress coinbaseAddress = new AionAddress(getCoinbaseAddress(pool).toByteArray());
        RULE.kernel.adjustBalance(coinbaseAddress, BigInteger.valueOf(blockRewards));
        incrementBlockNumber();
    }

    private long getBlockNumber() {
        return RULE.kernel.getBlockNumber();
    }

    private void incrementBlockNumber() {
        tweakBlockNumber(getBlockNumber() + 1);
    }

    private void tweakBlockNumber(long number) {
        try {
            Field f = TestingState.class.getDeclaredField("blockNumber");
            f.setAccessible(true);

            f.set(RULE.kernel, number);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
    private Address getCoinbaseAddress(Address pool){
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("getCoinbaseAddress")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        return (Address) result.getDecodedReturnData();
    }
}
