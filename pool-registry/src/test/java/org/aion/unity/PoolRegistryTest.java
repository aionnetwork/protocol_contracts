package org.aion.unity;

import avm.Address;
import org.aion.avm.core.util.LogSizeUtils;
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
import java.util.Arrays;
import java.util.Scanner;

import static org.junit.Assert.*;

public class PoolRegistryTest {

    private static BigInteger ENOUGH_BALANCE_TO_TRANSACT = BigInteger.TEN.pow(18 + 5);

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

        byte[] arguments = ABIUtil.encodeDeploymentArguments(stakerRegistry);
        byte[] data = RULE.getDappBytes(PoolRegistry.class, arguments, PoolState.class, PoolRewardsStateMachine.class, Decimal.class, PoolRegistryEvents.class);
        AvmRule.ResultWrapper result = RULE.deploy(preminedAddress, BigInteger.ZERO, data);
        assertTrue(result.getReceiptStatus().isSuccess());
        poolRegistry = result.getDappAddress();
    }

    public Address setupNewPool(int fee) {
        Address newPool = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        // STEP-1 register a new pool
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("registerPool")
                .encodeOneAddress(newPool)
                .encodeOneInteger(fee)
                .encodeOneByteArray("https://".getBytes())
                .encodeOneByteArray(new byte[32])
                .toBytes();

        AvmRule.ResultWrapper result = RULE.call(newPool, poolRegistry, BigInteger.ZERO, txData, 2_000_000L, 1L);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // STEP-2 do self-stake
        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(newPool)
                .toBytes();
        result = RULE.call(newPool, poolRegistry, nStake(1), txData);
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
                .encodeOneAddress(poolRegistry)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(stake.longValue(), result.getDecodedReturnData());
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
                .encodeOneLong(unstake.longValue())
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        long id = (long) result.getDecodedReturnData();
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool)
                .encodeOneAddress(poolRegistry)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(stake.longValue() - unstake.longValue(), result.getDecodedReturnData());

        tweakBlockNumber(getBlockNumber() +  6 * 60 * 24 * 7);

        // release the pending unvote
        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeUnvote")
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
                .encodeOneString("transferStake")
                .encodeOneAddress(pool1)
                .encodeOneAddress(pool2)
                .encodeOneLong(1)
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
        long stake = (Long) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 1L, stake);
        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool2)
                .toBytes();
        result = RULE.call(delegator, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (Long) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 1L, stake);

        // and from the pool registry
        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool1)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (Long) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 1L, stake);
        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool2)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (Long) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 1L, stake);
    }

    @Test
    public void testUsecaseAutoRedelegate() {
        Address delegator = preminedAddress;
        Address pool = setupNewPool(4);

        // delegate stake
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(delegator, poolRegistry, nStake(1), txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // enable auto delegation with 50% fee
        txData = new ABIStreamingEncoder()
                .encodeOneString("enableAutoRewardsDelegation")
                .encodeOneAddress(pool)
                .encodeOneInteger(20)
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
                .encodeOneAddress(poolRegistry)
                .toBytes();
        result = RULE.call(delegator, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long reward = (100 - 4) / 2;
        assertEquals(nStake(1).longValue() + (reward - reward * 20 / 100), result.getDecodedReturnData());
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
                .encodeOneString("withdraw")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(user1, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Long amount = (Long) result.getDecodedReturnData();
        assertEquals(3, amount.longValue());
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
                .encodeOneString("redelegate")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // The pool generates another block
        generateBlock(pool, 1000);

        // User1 withdraw
        txData = new ABIStreamingEncoder()
                .encodeOneString("withdraw")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Long amount = (Long) result.getDecodedReturnData();
        assertEquals(532, amount.longValue());

        // Check the stake owned by pool registry
        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool)
                .encodeOneAddress(poolRegistry)
                .toBytes();
        result = RULE.call(delegator, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Long stake = (Long) result.getDecodedReturnData();
        assertEquals(1450, stake.longValue());
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
        long stake = (long) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 2L, stake);

        // query the self stake of the pool
        txData = new ABIStreamingEncoder()
                .encodeOneString("getSelfStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (Long) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 1L, stake);

        // query the self stake of the pool
        txData = new ABIStreamingEncoder()
                .encodeOneString("getSelfBondStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (long) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 1L, stake);
    }

    @Test
    public void delegateAndVoteSelfStake(){
        Address pool = setupNewPool(4);
        Address delegator = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(pool, poolRegistry, BigInteger.ONE, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getSelfStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long stake = (long) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 1L, stake);

        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (Long) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 1L, stake);

        // vote as identity address
        txData = new ABIStreamingEncoder()
                .encodeOneString("vote")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, stakerRegistry, BigInteger.ONE, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getSelfStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (Long) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 1L, stake);

        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (Long) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 2L, stake);

        // vote as external delegator address
        txData = new ABIStreamingEncoder()
                .encodeOneString("vote")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, stakerRegistry, BigInteger.ONE, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getSelfStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (Long) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 1L, stake);

        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (Long) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 3L, stake);

        // NOTE: since vote is directly called, the getStake values retrieved from registry contracts are different
        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(delegator, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (Long) result.getDecodedReturnData();
        assertEquals(1L, stake);

        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (Long) result.getDecodedReturnData();
        assertEquals(0L, stake);

        // delegate  as external delegator address
        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ONE, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getSelfStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (Long) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 1L, stake);

        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (Long) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 4L, stake);
    }

    @Test
    public void delegateAndUnvoteSelfStake(){
        Address pool = setupNewPool(4);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(pool, poolRegistry, BigInteger.TEN, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getSelfStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long stake = (long) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 10L, stake);

        // unvote as identity address will fail, only the dedicated method for unbonding the self stake should be called
        txData = new ABIStreamingEncoder()
                .encodeOneString("unvote")
                .encodeOneAddress(pool)
                .encodeOneLong(1L)
                .toBytes();
        result = RULE.call(pool, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isFailed());

        // undelegate as external delegator address
        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneLong(1L)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long id = (long) result.getDecodedReturnData();
        BigInteger balanceBeforeFinalization = RULE.kernel.getBalance(new AionAddress(pool.toByteArray()));

        txData = new ABIStreamingEncoder()
                .encodeOneString("getSelfStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (Long) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 9L, stake);

        // query the self stake of the pool
        txData = new ABIStreamingEncoder()
                .encodeOneString("getSelfBondStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (Long) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 9L, stake);

        tweakBlockNumber(getBlockNumber() +  6 * 60 * 24 * 7);

        // release the pending unvote
        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeUnvote")
                .encodeOneLong(id)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        Assert.assertEquals(BigInteger.ONE, (RULE.kernel.getBalance(new AionAddress(pool.toByteArray()))).subtract(balanceBeforeFinalization));
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
                .encodeOneString("transferStake")
                .encodeOneAddress(pool1)
                .encodeOneAddress(pool2)
                .encodeOneLong(10)
                .toBytes();
        result = RULE.call(pool1, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isFailed());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool2)
                .toBytes();
        result = RULE.call(pool1, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long stake = (long) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue(), stake);
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
                .encodeOneString("transferStake")
                .encodeOneAddress(pool1)
                .encodeOneAddress(pool2)
                .encodeOneLong(10)
                .toBytes();
        result = RULE.call(pool2, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isFailed());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getSelfStake")
                .encodeOneAddress(pool2)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long stake = (long) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue(), stake);
    }

    @Test
    public void testCommissionRateUpdate() {
        Address pool = setupNewPool(10);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("updateCommissionRate")
                .encodeOneAddress(pool)
                .encodeOneInteger(20)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        // validate the log
        assertEquals(1, result.getLogs().size());
        assertArrayEquals(LogSizeUtils.truncatePadTopic("ADSCommissionRateUpdated".getBytes()),
                result.getLogs().get(0).copyOfTopics().get(0));
        assertArrayEquals(pool.toByteArray(), result.getLogs().get(0).copyOfTopics().get(1));
        assertArrayEquals(BigInteger.valueOf(20).toByteArray(), result.getLogs().get(0).copyOfData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("updateCommissionRate")
                .encodeOneAddress(pool)
                .encodeOneInteger(30)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isFailed());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getPoolInfo")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        byte[] info = (byte[]) result.getDecodedReturnData();
        byte[] expected = new byte[]{0, 0, 0, 20};
        Assert.assertArrayEquals(expected, Arrays.copyOfRange(info, 32 * 2 + 2 + 1, 32 * 2 + 2 + 1 + 4));
    }

    @Test
    public void testMetaDataUpdate() {
        Address pool = setupNewPool(10);
        byte[] newMetaDataUrl = "http://".getBytes();
        byte[] newMetaDataContentHash = new byte[32];
        Arrays.fill(newMetaDataContentHash, Byte.MIN_VALUE);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("updateMetaDataUrl")
                .encodeOneAddress(pool)
                .encodeOneByteArray(newMetaDataUrl)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        // validate the log
        assertEquals(1, result.getLogs().size());
        assertArrayEquals(LogSizeUtils.truncatePadTopic("ADSMetaDataUrlUpdated".getBytes()),
                result.getLogs().get(0).copyOfTopics().get(0));
        assertArrayEquals(pool.toByteArray(), result.getLogs().get(0).copyOfTopics().get(1));
        assertArrayEquals(newMetaDataUrl, result.getLogs().get(0).copyOfData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("updateMetaDataContentHash")
                .encodeOneAddress(pool)
                .encodeOneByteArray(newMetaDataContentHash)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        // validate the log
        assertEquals(1, result.getLogs().size());
        assertArrayEquals(LogSizeUtils.truncatePadTopic("ADSMetaDataContentHashUpdated".getBytes()),
                result.getLogs().get(0).copyOfTopics().get(0));
        assertArrayEquals(pool.toByteArray(), result.getLogs().get(0).copyOfTopics().get(1));
        assertArrayEquals(newMetaDataContentHash, result.getLogs().get(0).copyOfData());
    }

    /**
     * N unit of MIN_SELF_STAKE.
     *
     * @param n
     * @return
     */
    private BigInteger nStake(int n) {
        return PoolRegistry.MIN_SELF_STAKE.multiply(BigInteger.valueOf(n));
    }

    private void generateBlock(Address pool, long blockRewards) {
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("getCoinbaseAddressForIdentityAddress")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Address coinbaseAddress = (Address) result.getDecodedReturnData();
        RULE.balanceTransfer(preminedAddress, coinbaseAddress, BigInteger.valueOf(blockRewards), 1_000_000L, 1);
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
}

