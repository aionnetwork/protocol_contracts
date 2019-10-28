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
import java.util.Scanner;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class PoolRegistryEventTest {

    private static BigInteger ENOUGH_BALANCE_TO_TRANSACT = BigInteger.TEN.pow(18 + 5);
    private static BigInteger MIN_SELF_STAKE = new BigInteger("1000000000000000000000");

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

        long COMMISSION_RATE_CHANGE_TIME_LOCK_PERIOD = 6 * 60 * 24 * 7;
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

        assertEquals(2, result.getLogs().size());

        Log stakerRegistryEvent = result.getLogs().get(0);
        assertArrayEquals(LogSizeUtils.truncatePadTopic("StakerRegistered".getBytes()), stakerRegistryEvent.copyOfTopics().get(0));
        assertArrayEquals(newPool.toByteArray(), stakerRegistryEvent.copyOfTopics().get(1));
        assertArrayEquals(newPool.toByteArray(), stakerRegistryEvent.copyOfTopics().get(2));
        assertArrayEquals(poolRegistry.toByteArray(), stakerRegistryEvent.copyOfData());

        Log poolRegistryEvent = result.getLogs().get(1);
        assertArrayEquals(LogSizeUtils.truncatePadTopic("ADSPoolRegistered".getBytes()), poolRegistryEvent.copyOfTopics().get(0));
        assertArrayEquals(newPool.toByteArray(), poolRegistryEvent.copyOfTopics().get(1));
        assertEquals(fee, new BigInteger(poolRegistryEvent.copyOfTopics().get(2)).intValue());
        assertArrayEquals(new byte[32], poolRegistryEvent.copyOfTopics().get(3));
        assertEquals("https://", new String(poolRegistryEvent.copyOfData()));

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

    private BigInteger nStake(int n) {
        return MIN_SELF_STAKE.multiply(BigInteger.valueOf(n));
    }

    @Test
    public void delegateEventTest() {
        Address pool = setupNewPool(10);
        BigInteger stake = nStake(1);
        Address delegator = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, stake, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        assertEquals(2, result.getLogs().size());
        Log stakerRegistryEvent = result.getLogs().get(0);
        assertArrayEquals(LogSizeUtils.truncatePadTopic("Bonded".getBytes()), stakerRegistryEvent.copyOfTopics().get(0));
        assertArrayEquals(pool.toByteArray(), stakerRegistryEvent.copyOfTopics().get(1));
        assertEquals(stake, new BigInteger(stakerRegistryEvent.copyOfData()));

        Log poolRegistryEvent = result.getLogs().get(1);
        assertArrayEquals(LogSizeUtils.truncatePadTopic("ADSDelegated".getBytes()), poolRegistryEvent.copyOfTopics().get(0));
        assertArrayEquals(delegator.toByteArray(), poolRegistryEvent.copyOfTopics().get(1));
        assertArrayEquals(pool.toByteArray(), poolRegistryEvent.copyOfTopics().get(2));
        assertEquals(stake, new BigInteger(poolRegistryEvent.copyOfData()));

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, stake, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        assertEquals(2, result.getLogs().size());
        stakerRegistryEvent = result.getLogs().get(0);
        assertArrayEquals(LogSizeUtils.truncatePadTopic("Bonded".getBytes()), stakerRegistryEvent.copyOfTopics().get(0));
        assertArrayEquals(pool.toByteArray(), stakerRegistryEvent.copyOfTopics().get(1));
        assertEquals(stake, new BigInteger(stakerRegistryEvent.copyOfData()));

        poolRegistryEvent = result.getLogs().get(1);
        assertArrayEquals(LogSizeUtils.truncatePadTopic("ADSDelegated".getBytes()), poolRegistryEvent.copyOfTopics().get(0));
        assertArrayEquals(delegator.toByteArray(), poolRegistryEvent.copyOfTopics().get(1));
        assertArrayEquals(pool.toByteArray(), poolRegistryEvent.copyOfTopics().get(2));
        assertEquals(stake, new BigInteger(poolRegistryEvent.copyOfData()));
    }

    @Test
    public void undelegateEventTest() {
        Address pool = setupNewPool(10);
        BigInteger stake = nStake(1);
        BigInteger unstake = BigInteger.valueOf(10000);
        BigInteger fee = BigInteger.TEN;
        Address delegator = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

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
                .encodeOneBigInteger(fee)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long id = (long) result.getDecodedReturnData();

        assertEquals(2, result.getLogs().size());
        Log stakerRegistryEvent = result.getLogs().get(0);
        assertArrayEquals(LogSizeUtils.truncatePadTopic("Unbonded".getBytes()), stakerRegistryEvent.copyOfTopics().get(0));
        assertEquals(id, new BigInteger(stakerRegistryEvent.copyOfTopics().get(1)).longValue());
        assertArrayEquals(pool.toByteArray(), stakerRegistryEvent.copyOfTopics().get(2));
        assertArrayEquals(delegator.toByteArray(), stakerRegistryEvent.copyOfTopics().get(3));
        ABIDecoder decoder = new ABIDecoder(stakerRegistryEvent.copyOfData());
        assertEquals(unstake, decoder.decodeOneBigInteger());
        assertEquals(BigInteger.TEN, decoder.decodeOneBigInteger());

        Log poolRegistryEvent = result.getLogs().get(1);
        assertArrayEquals(LogSizeUtils.truncatePadTopic("ADSUndelegated".getBytes()), poolRegistryEvent.copyOfTopics().get(0));
        assertEquals(id, new BigInteger(poolRegistryEvent.copyOfTopics().get(1)).longValue());
        assertArrayEquals(delegator.toByteArray(), poolRegistryEvent.copyOfTopics().get(2));
        assertArrayEquals(pool.toByteArray(), poolRegistryEvent.copyOfTopics().get(3));
        decoder = new ABIDecoder(poolRegistryEvent.copyOfData());
        assertEquals(unstake, decoder.decodeOneBigInteger());
        assertEquals(BigInteger.TEN, decoder.decodeOneBigInteger());
    }

    @Test
    public void transferDelegationEventTest() {
        Address pool1 = setupNewPool(10);
        Address pool2 = setupNewPool(10);
        BigInteger stake = nStake(2);
        BigInteger transferValue = nStake(1);
        BigInteger fee = BigInteger.TEN;
        Address delegator = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        // delegate 2 stake
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool1)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, stake, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // transfer 1 stake
        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(pool1)
                .encodeOneAddress(pool2)
                .encodeOneBigInteger(transferValue)
                .encodeOneBigInteger(fee)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        long id = (long) result.getDecodedReturnData();
        assertTrue(result.getReceiptStatus().isSuccess());

        assertEquals(2, result.getLogs().size());
        Log stakerRegistryEvent = result.getLogs().get(0);
        assertArrayEquals(LogSizeUtils.truncatePadTopic("StakeTransferred".getBytes()), stakerRegistryEvent.copyOfTopics().get(0));
        assertEquals(id, new BigInteger(stakerRegistryEvent.copyOfTopics().get(1)).longValue());
        assertArrayEquals(pool1.toByteArray(), stakerRegistryEvent.copyOfTopics().get(2));
        assertArrayEquals(pool2.toByteArray(), stakerRegistryEvent.copyOfTopics().get(3));
        ABIDecoder decoder = new ABIDecoder(stakerRegistryEvent.copyOfData());
        assertEquals(transferValue, decoder.decodeOneBigInteger());
        assertEquals(fee, decoder.decodeOneBigInteger());

        Log poolRegistryEvent = result.getLogs().get(1);
        assertArrayEquals(LogSizeUtils.truncatePadTopic("ADSDelegationTransferred".getBytes()), poolRegistryEvent.copyOfTopics().get(0));
        assertEquals(id, new BigInteger(poolRegistryEvent.copyOfTopics().get(1)).longValue());
        assertArrayEquals(delegator.toByteArray(), poolRegistryEvent.copyOfTopics().get(2));
        assertArrayEquals(pool1.toByteArray(), poolRegistryEvent.copyOfTopics().get(3));
        decoder = new ABIDecoder(poolRegistryEvent.copyOfData());
        assertEquals(pool2, decoder.decodeOneAddress());
        assertEquals(transferValue, decoder.decodeOneBigInteger());
        assertEquals(fee, decoder.decodeOneBigInteger());
    }

    @Test
    public void withdrawEventTest(){
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
        assertEquals(1, result.getTransactionResult().logs.size());

        Log poolRegistryEvent = result.getLogs().get(0);
        assertArrayEquals(LogSizeUtils.truncatePadTopic("ADSWithdrew".getBytes()), poolRegistryEvent.copyOfTopics().get(0));
        assertArrayEquals(user1.toByteArray(), poolRegistryEvent.copyOfTopics().get(1));
        assertArrayEquals(pool.toByteArray(), poolRegistryEvent.copyOfTopics().get(2));
        assertEquals(amount, new BigInteger(poolRegistryEvent.copyOfData()));
    }

    private void generateBlock(Address pool, long blockRewards) {
        AionAddress coinbaseAddress = new AionAddress(getCoinbaseAddress(pool).toByteArray());
        RULE.kernel.adjustBalance(coinbaseAddress, BigInteger.valueOf(blockRewards));
        incrementBlockNumber();
    }

    private void incrementBlockNumber() {
        tweakBlockNumber(RULE.kernel.getBlockNumber() + 1);
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
