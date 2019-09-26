package org.aion.unity;

import avm.Address;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.core.util.LogSizeUtils;
import org.aion.avm.embed.AvmRule;
import org.aion.avm.tooling.ABIUtil;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;
import org.aion.kernel.TestingState;
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

public class PoolStateTest {

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
        validateState(newPool, true);

        return newPool;
    }

    @Test
    public void transferToActivePoolNoFee() {
        Address pool1 = setupNewPool(10);
        Address pool2 = setupNewPool(10);
        Address delegator = RULE.getRandomAddress(nStake(1000));

        BigInteger remainingCapacity = nStake(99);
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool1)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, remainingCapacity, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool1, true);

        // transfer to active pool
        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(pool1)
                .encodeOneAddress(pool2)
                .encodeOneBigInteger(remainingCapacity)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long id = (long) result.getDecodedReturnData();
        validateState(pool1, true);
        validateState(pool2, true);

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool1)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, nStake(1), txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool1, true);

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool2)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, nStake(1), txData);
        assertTrue(result.getReceiptStatus().isFailed());
        validateState(pool2, true);

        tweakBlockNumber(RULE.kernel.getBlockNumber() + 6 * 10);

        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeTransfer")
                .encodeOneLong(id)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool2)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, nStake(1), txData);
        assertTrue(result.getReceiptStatus().isFailed());
        validateState(pool2, true);
    }

    @Test
    public void multipleTransferToActivePool() {
        Address pool1 = setupNewPool(10);
        Address pool2 = setupNewPool(10);
        Address delegator1 = RULE.getRandomAddress(nStake(1000));
        Address delegator2 = RULE.getRandomAddress(nStake(1000));

        BigInteger remainingCapacity = nStake(99);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool1)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator1, poolRegistry, remainingCapacity.divide(BigInteger.TWO), txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool1, true);

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool1)
                .toBytes();
        result = RULE.call(delegator2, poolRegistry, remainingCapacity.divide(BigInteger.TWO), txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool1, true);

        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(pool1)
                .encodeOneAddress(pool2)
                .encodeOneBigInteger(remainingCapacity.divide(BigInteger.TWO))
                .encodeOneBigInteger(BigInteger.TEN)
                .toBytes();
        result = RULE.call(delegator1, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool1, true);
        validateState(pool2, true);

        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(pool1)
                .encodeOneAddress(pool2)
                .encodeOneBigInteger(remainingCapacity.divide(BigInteger.TWO))
                .encodeOneBigInteger(BigInteger.TEN)
                .toBytes();
        result = RULE.call(delegator2, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool1, true);
        validateState(pool2, true);

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool1)
                .toBytes();
        result = RULE.call(delegator2, poolRegistry, nStake(1), txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool1, true);

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool2)
                .toBytes();
        result = RULE.call(delegator1, poolRegistry, nStake(1), txData);
        assertTrue(result.getReceiptStatus().isFailed());

        // fee is not included in pending stake, and can be delegated
        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool2)
                .toBytes();
        result = RULE.call(delegator2, poolRegistry, BigInteger.TEN, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // transfer to full pool will fail
        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(pool1)
                .encodeOneAddress(pool2)
                .encodeOneBigInteger(nStake(1))
                .encodeOneBigInteger(BigInteger.TEN)
                .toBytes();
        result = RULE.call(delegator2, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isFailed());
        validateState(pool1, true);
        validateState(pool2, true);

        // fee is not included in pending stake, and can be transferred
        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(pool1)
                .encodeOneAddress(pool2)
                .encodeOneBigInteger(BigInteger.TEN)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(delegator2, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool1, true);
        validateState(pool2, true);
    }

    @Test
    public void transferAndUndelegateFromPool() {
        Address pool1 = setupNewPool(10);
        Address pool2 = setupNewPool(10);
        Address delegator1 = RULE.getRandomAddress(nStake(1000));
        Address delegator2 = RULE.getRandomAddress(nStake(1000));

        BigInteger remainingCapacity = nStake(99);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool1)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator1, poolRegistry, remainingCapacity, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool2)
                .toBytes();
        result = RULE.call(delegator2, poolRegistry, remainingCapacity.divide(BigInteger.TWO), txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(pool1)
                .encodeOneAddress(pool2)
                .encodeOneBigInteger(remainingCapacity.divide(BigInteger.TWO))
                .encodeOneBigInteger(BigInteger.TEN)
                .toBytes();
        result = RULE.call(delegator1, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(pool1)
                .encodeOneAddress(pool2)
                .encodeOneBigInteger(remainingCapacity.divide(BigInteger.TWO))
                .encodeOneBigInteger(BigInteger.TEN)
                .toBytes();
        result = RULE.call(delegator1, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isFailed());

        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool2)
                .encodeOneBigInteger(remainingCapacity.divide(BigInteger.TWO))
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(delegator2, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool2, true);

        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(pool1)
                .encodeOneAddress(pool2)
                .encodeOneBigInteger(remainingCapacity.divide(BigInteger.TWO))
                .encodeOneBigInteger(BigInteger.TEN)
                .toBytes();
        result = RULE.call(delegator1, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool2, true);
    }

    @Test
    public void transferToBrokenPool() {
        Address pool1 = setupNewPool(10);
        Address pool2 = setupNewPool(10);
        Address delegator1 = RULE.getRandomAddress(nStake(1000));
        Address delegator2 = RULE.getRandomAddress(nStake(1000));

        BigInteger remainingCapacity = nStake(99);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool1)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator1, poolRegistry, remainingCapacity, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool2)
                .toBytes();
        result = RULE.call(delegator2, poolRegistry, remainingCapacity, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool2)
                .encodeOneBigInteger(nStake(1))
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(pool2, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool2, false);

        // transfer to a broken pool will fail
        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(pool1)
                .encodeOneAddress(pool2)
                .encodeOneBigInteger(remainingCapacity.divide(BigInteger.TWO))
                .encodeOneBigInteger(BigInteger.TEN)
                .toBytes();
        result = RULE.call(delegator1, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isFailed());
        validateState(pool1, true);
        validateState(pool2, false);
    }

    @Test
    public void transferChangeFromPoolStateToActive() {
        Address pool1 = setupNewPool(10);
        Address pool2 = setupNewPool(10);
        Address delegator1 = RULE.getRandomAddress(nStake(1000));

        BigInteger remainingCapacity = nStake(198);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool1)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(pool1, poolRegistry, nStake(1), txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool1, true);

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool2)
                .toBytes();
        result = RULE.call(pool2, poolRegistry, nStake(1), txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool2, true);

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool1)
                .toBytes();
        result = RULE.call(delegator1, poolRegistry, remainingCapacity, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool1, true);

        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool1)
                .encodeOneBigInteger(nStake(1))
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(pool1, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool1, false);

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool1)
                .toBytes();
        result = RULE.call(delegator1, poolRegistry, BigInteger.TEN, txData);
        assertTrue(result.getReceiptStatus().isFailed());

        // transfer to a broken pool will fail
        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(pool1)
                .encodeOneAddress(pool2)
                .encodeOneBigInteger(remainingCapacity)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(delegator1, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool1, true);
        validateState(pool2, true);

        // from pool becomes active after the transfer
        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool1)
                .toBytes();
        result = RULE.call(delegator1, poolRegistry, BigInteger.TEN, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool1, true);
    }

    @Test
    public void revertDelegateWhichBreaksPool() {
        Address pool = setupNewPool(10);
        Address delegator = RULE.getRandomAddress(nStake(1000));

        BigInteger remainingCapacity = nStake(100);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, remainingCapacity, txData);
        assertTrue(result.getReceiptStatus().isFailed());
        validateState(pool, true);
    }

    @Test
    public void delegationFromOperatorActivatesPool() {
        Address pool = setupNewPool(10);
        Address delegator = RULE.getRandomAddress(nStake(1000));

        BigInteger remainingCapacity = nStake(99);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, remainingCapacity.divide(BigInteger.TWO), txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // put the pool into a broken state
        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneBigInteger(nStake(1))
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool, false);

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.TEN, txData);
        assertTrue(result.getReceiptStatus().isFailed());

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, poolRegistry, nStake(1), txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool, true);

        // pool becomes active after the delegate
        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, remainingCapacity.divide(BigInteger.TWO), txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool, true);
    }

    @Test
    public void undelegationFromOperatorBreaksPool() {
        Address pool = setupNewPool(10);
        Address delegator = RULE.getRandomAddress(nStake(1000));

        BigInteger remainingCapacity = nStake(99);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, remainingCapacity.divide(BigInteger.TWO), txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // put the pool into a broken state
        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneBigInteger(nStake(1))
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool, false);

        // validate the event from staker registry
        assertEquals(3, result.getLogs().size());
        Log changedLog = result.getLogs().get(1);
        assertArrayEquals(LogSizeUtils.truncatePadTopic("StateChanged".getBytes()), changedLog.copyOfTopics().get(0));
        assertEquals(pool, new Address(changedLog.copyOfTopics().get(1)));
        assertArrayEquals(new byte[]{0}, changedLog.copyOfData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.TEN, txData);
        assertTrue(result.getReceiptStatus().isFailed());
    }

    @Test
    public void undelegateChangePoolStateToActive() {
        Address pool = setupNewPool(10);
        Address delegator = RULE.getRandomAddress(nStake(1000));

        BigInteger remainingCapacity = nStake(197);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(pool, poolRegistry, nStake(1), txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool, true);

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, remainingCapacity, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool, true);

        // undelegate puts the pool into a broken state
        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneBigInteger(nStake(1))
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool, false);

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.TEN, txData);
        assertTrue(result.getReceiptStatus().isFailed());

        // undelegate puts the pool into an active state
        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneBigInteger(remainingCapacity)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool, true);

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, nStake(90), txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        validateState(pool, true);
    }

    private void validateState(Address pool, boolean expectedState){
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("isActive")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(pool, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(expectedState, result.getDecodedReturnData());
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

    private BigInteger nStake(int n) {
        return MIN_SELF_STAKE.multiply(BigInteger.valueOf(n));
    }

}
