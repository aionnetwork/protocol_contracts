package org.aion.unity;

import avm.Address;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.embed.AvmRule;
import org.aion.avm.tooling.ABIUtil;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;
import org.aion.kernel.TestingState;
import org.aion.types.AionAddress;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.Scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StateMachineTest {

    private static BigInteger ENOUGH_BALANCE_TO_TRANSACT = BigInteger.TEN.pow(18 + 5);
    private static BigInteger MIN_SELF_STAKE = new BigInteger("1000000000000000000000");
    private static long COMMISSION_RATE_CHANGE_TIME_LOCK_PERIOD = 6 * 60 * 24 * 7;

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
        poolRegistry = result.getDappAddress();
    }

    private Address setupNewPool(int fee) {
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

        AvmRule.ResultWrapper result = RULE.call(newPool, poolRegistry, MIN_SELF_STAKE, txData, 2_000_000L, 1L);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // verify now
        txData = new ABIStreamingEncoder()
                .encodeOneString("getPoolStatus")
                .encodeOneAddress(newPool)
                .toBytes();
        result = RULE.call(newPool, poolRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals("ACTIVE", result.getDecodedReturnData());

        return newPool;
    }

    @Test
    public void testDelegate() {
        Address pool = setupNewPool(4);
        Address delegator = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, nStake(1), txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        generateBlock(pool, 1000);

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, nStake(1), txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        generateBlock(pool, 1000);
        long expectedDelegatorRewards = (1000 - 40) / 2 + ((1000 - 40) * 2 / 3);

        txData = new ABIStreamingEncoder()
                .encodeOneString("withdraw")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.valueOf(expectedDelegatorRewards), result.getDecodedReturnData());
    }

    @Test
    public void testUndelegate() {
        Address pool = setupNewPool(4);
        Address delegator = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, nStake(1), txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        generateBlock(pool, 1000);

        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneBigInteger(nStake(1))
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        generateBlock(pool, 1000);
        long expectedDelegatorRewards = (1000 - 40) / 2;
        long expectedPoolRewards = (1000 - 40) / 2 + 1000 + 40;

        txData = new ABIStreamingEncoder()
                .encodeOneString("withdraw")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.valueOf(expectedDelegatorRewards), result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("withdraw")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.valueOf(expectedPoolRewards), result.getDecodedReturnData());
    }

    @Test
    public void testTransfer() {
        Address pool1 = setupNewPool(4);
        Address pool2 = setupNewPool(4);

        Address delegator = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool1)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, nStake(1), txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        generateBlock(pool1, 1000);

        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(pool1)
                .encodeOneAddress(pool2)
                .encodeOneBigInteger(nStake(1))
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long id = (long) result.getDecodedReturnData();

        generateBlock(pool1, 1000);
        generateBlock(pool2, 1000);

        tweakBlockNumber(RULE.kernel.getBlockNumber() + 6 * 10);
        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeTransfer")
                .encodeOneLong(id)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        long expectedDelegatorRewards = (1000 - 40) / 2;

        txData = new ABIStreamingEncoder()
                .encodeOneString("withdraw")
                .encodeOneAddress(pool1)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.valueOf(expectedDelegatorRewards), result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("withdraw")
                .encodeOneAddress(pool2)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.ZERO, result.getDecodedReturnData());

        generateBlock(pool2, 1000);

        txData = new ABIStreamingEncoder()
                .encodeOneString("withdraw")
                .encodeOneAddress(pool2)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.valueOf(expectedDelegatorRewards), result.getDecodedReturnData());
    }

    @Test
    public void testAutoDelegateRewards() {
        Address pool = setupNewPool(4);
        Address delegator = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, nStake(1), txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        generateBlock(pool, 1000);

        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneBigInteger(nStake(1))
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // enable auto delegation with 10% fee
        txData = new ABIStreamingEncoder()
                .encodeOneString("enableAutoRewardsDelegation")
                .encodeOneAddress(pool)
                .encodeOneInteger(100000)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // some third party calls autoDelegateRewards
        Address random = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);
        txData = new ABIStreamingEncoder()
                .encodeOneString("autoDelegateRewards")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(random, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        generateBlock(pool, 1000);

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, nStake(1), txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        generateBlock(pool, 1000);

        BigInteger expectedDelegatorRewards = BigInteger.valueOf(1000 - 40).multiply(new BigInteger("1000000000000000000432"))
                .divide(new BigInteger("1000000000000000000000").add(new BigInteger("1000000000000000000432")));

        txData = new ABIStreamingEncoder()
                .encodeOneString("withdraw")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        // NOTE: due to rounding error in calculating rewards, the value is 1 nAmp less
        Assert.assertEquals(expectedDelegatorRewards.subtract(BigInteger.ONE), result.getDecodedReturnData());
    }


    @Test
    public void testGetRewards(){
        Address pool = setupNewPool(4);
        Address delegator1 = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);
        Address delegator2 = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator1, poolRegistry, nStake(1), txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        generateBlock(pool, 1000);

        long expectedDelegatorRewards = (1000 - 40) / 2;
        long expectedPoolRewards = (1000 - 40) / 2 + 40;

        txData = new ABIStreamingEncoder()
                .encodeOneString("getRewards")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator1)
                .toBytes();
        result = RULE.call(delegator1, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.valueOf(expectedDelegatorRewards), result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getRewards")
                .encodeOneAddress(pool)
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.valueOf(expectedPoolRewards), result.getDecodedReturnData());

        generateBlock(pool, 1000);

        expectedDelegatorRewards += (1000 - 40) / 2;
        expectedPoolRewards += (1000 - 40) / 2 + 40;

        txData = new ABIStreamingEncoder()
                .encodeOneString("getRewards")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator1)
                .toBytes();
        result = RULE.call(delegator1, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.valueOf(expectedDelegatorRewards), result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getRewards")
                .encodeOneAddress(pool)
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.valueOf(expectedPoolRewards), result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator2, poolRegistry, nStake(1), txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        generateBlock(pool, 1000);

        expectedDelegatorRewards += (1000 - 40) / 3;
        expectedPoolRewards += (1000 - 40) / 3 + 40;

        txData = new ABIStreamingEncoder()
                .encodeOneString("getRewards")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator1)
                .toBytes();
        result = RULE.call(delegator1, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.valueOf(expectedDelegatorRewards), result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getRewards")
                .encodeOneAddress(pool)
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.valueOf(expectedPoolRewards), result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("withdraw")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator1, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.valueOf(expectedDelegatorRewards), result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("withdraw")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.valueOf(expectedPoolRewards), result.getDecodedReturnData());
    }

    private long getBlockNumber() {
        return RULE.kernel.getBlockNumber();
    }

    private void generateBlock(Address pool, long blockRewards) {
        AionAddress coinbaseAddress = new AionAddress(getCoinbaseAddress(pool).toByteArray());
        RULE.kernel.adjustBalance(coinbaseAddress, BigInteger.valueOf(blockRewards));
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

    private Address getCoinbaseAddress(Address pool) {
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("getCoinbaseAddress")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        return (Address) result.getDecodedReturnData();
    }

    private BigInteger nStake(int n) {
        return MIN_SELF_STAKE.multiply(BigInteger.valueOf(n));
    }
}
