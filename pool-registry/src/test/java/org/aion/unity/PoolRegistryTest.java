package org.aion.unity;

import avm.Address;
import org.aion.avm.core.util.LogSizeUtils;
import org.aion.avm.embed.AvmRule;
import org.aion.avm.tooling.ABIUtil;
import org.aion.avm.userlib.abi.ABIDecoder;
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

        byte[] arguments = ABIUtil.encodeDeploymentArguments(stakerRegistry);
        byte[] data = RULE.getDappBytes(PoolRegistry.class, arguments, 1, PoolStorageObjects.class, PoolRewardsStateMachine.class, PoolRegistryEvents.class, PoolRegistryStorage.class);

        AvmRule.ResultWrapper result = RULE.deploy(preminedAddress, BigInteger.ZERO, data);
        assertTrue(result.getReceiptStatus().isSuccess());
        poolRegistry = result.getDappAddress();
    }

    public Address setupNewPool(int fee) {
        fee = fee * 10000;
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
        assertEquals(stake, result.getDecodedReturnData());
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
        assertEquals(stake.longValue() - unstake.longValue(), ((BigInteger) result.getDecodedReturnData()).longValue());

        tweakBlockNumber(getBlockNumber() +  6 * 60 * 24 * 7);

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
        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool2)
                .toBytes();
        result = RULE.call(delegator, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(nStake(1).add(BigInteger.ONE), stake);

        // and from the pool registry
        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool1)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(nStake(1).add(BigInteger.ONE), stake);

        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool2)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (BigInteger) result.getDecodedReturnData();
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
                .encodeOneAddress(poolRegistry)
                .toBytes();
        result = RULE.call(delegator, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long reward = (100 - 4) / 2;
        assertEquals(nStake(1).longValue() + (reward - reward * 20 / 100), ((BigInteger)result.getDecodedReturnData()).longValue());
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
        BigInteger amount = (BigInteger) result.getDecodedReturnData();
        assertEquals(449, amount.longValue());

        // Check the stake owned by pool registry
        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool)
                .encodeOneAddress(poolRegistry)
                .toBytes();
        result = RULE.call(delegator, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        BigInteger stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(3875820019684213186L, stake.longValue());
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
        BigInteger stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(nStake(1).add(BigInteger.TWO), stake);

        // query the self stake of the pool
        txData = new ABIStreamingEncoder()
                .encodeOneString("getSelfStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(nStake(1).add(BigInteger.ONE), stake);

        // query the self stake of the pool
        txData = new ABIStreamingEncoder()
                .encodeOneString("getSelfBondStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(nStake(1).add(BigInteger.ONE), stake);
    }

    /**
     * Tests the case where delegation is done through the StakerRegistry
     */
    @Test
    public void delegateAndDelegateSelfStake(){
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
        BigInteger stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 1L, stake.longValue());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 1L, stake.longValue());

        // delegate as identity address
        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
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
        stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 1L, stake.longValue());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 2L, stake.longValue());

        // delegate as external delegator address
        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
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
        stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 1L, stake.longValue());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 3L, stake.longValue());

        // NOTE: since delegate is directly called from the StakerRegistry, the getStake values retrieved from registry contracts are different
        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(delegator, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(1L, stake.longValue());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool)
                .encodeOneAddress(delegator)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(0L, stake.longValue());

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
        stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 1L, stake.longValue());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 4L, stake.longValue());
    }

    /**
     * Tests the case where un-delegation is done through the StakerRegistry
     */
    @Test
    public void delegateAndUndelegateSelfStake(){
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
        BigInteger stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 10L, stake.longValue());

        // undelegate as identity address will fail, only the dedicated method for unbonding the self stake should be called
        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneLong(1L)
                .toBytes();
        result = RULE.call(pool, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isFailed());

        // undelegate as external delegator address
        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneBigInteger(BigInteger.ONE)
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
        stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 9L, stake.longValue());

        // query the self stake of the pool
        txData = new ABIStreamingEncoder()
                .encodeOneString("getSelfBondStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (BigInteger) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 9L, stake.longValue());

        tweakBlockNumber(getBlockNumber() +  6 * 60 * 24 * 7);

        // release the pending undelegate
        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeUndelegate")
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
                .encodeOneString("transferDelegation")
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
        BigInteger stake = (BigInteger) result.getDecodedReturnData();
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

        tweakBlockNumber(getBlockNumber() +  PoolRegistry.COMMISSION_RATE_CHANGE_TIME_LOCK_PERIOD);

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
                .encodeOneString("updateMetaDataUrl")
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

    @Test
    public void testGetPoolInfo(){
        Address pool = setupNewPool(10);
        byte[] metaDataUrl = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes();
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("updateMetaDataUrl")
                .encodeOneByteArray(metaDataUrl)
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
        Assert.assertArrayEquals(new byte[32], decoder.decodeOneByteArray());
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

        AvmRule.ResultWrapper result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData, 2_000_000L, 1L);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        Address delegator = RULE.getRandomAddress(nStake(105));

        // delegator stake 1 wei
        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ONE, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getPoolStatus")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals("BROKEN", result.getDecodedReturnData());

        // pool stake 1 wei
        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, poolRegistry, nStake(1), txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getPoolStatus")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals("ACTIVE", result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ONE, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, nStake(100), txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getPoolStatus")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(pool, poolRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals("BROKEN", result.getDecodedReturnData());
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
                .encodeOneString("withdraw")
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
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long id = (long) result.getDecodedReturnData();

        tweakBlockNumber(getBlockNumber() +  6 * 60 * 24 * 7);

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
                .encodeOneString("withdraw")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.valueOf(expectedDelegatorRewards), result.getDecodedReturnData());

        long expectedPoolRewards = (1000 - 40) / 2 + (1000 - 40) / 2 + 40 + 40;
        txData = new ABIStreamingEncoder()
                .encodeOneString("withdraw")
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
                .encodeOneString("withdraw")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.ZERO, result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("withdraw")
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
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long id1 = (long) result.getDecodedReturnData();

        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(pool)
                .encodeOneBigInteger(BigInteger.TEN)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        long id2 = (long) result.getDecodedReturnData();

        tweakBlockNumber(getBlockNumber() +  6 * 60 * 24 * 7);

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
                .encodeOneString("getSelfStake")
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
        RULE.kernel.adjustBalance(coinbaseAddress, RULE.kernel.getBalance(coinbaseAddress).add(BigInteger.valueOf(blockRewards)));
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
                .encodeOneString("getCoinbaseAddressForIdentityAddress")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        return (Address) result.getDecodedReturnData();
    }
}
