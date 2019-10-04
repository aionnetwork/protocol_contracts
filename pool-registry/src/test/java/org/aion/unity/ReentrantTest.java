package org.aion.unity;

import avm.Address;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.core.util.LogSizeUtils;
import org.aion.avm.embed.AvmRule;
import org.aion.avm.tooling.ABIUtil;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;
import org.aion.kernel.TestingState;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.aion.unity.resources.ReentrantContract;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.Scanner;

/**
 * This test ensures that reentrant calls to the contract, do not violate contract invariance:
 * - Additional stake being generated
 * - Additional rewards being withdrawn
 */
public class ReentrantTest {
    private static BigInteger ENOUGH_BALANCE_TO_TRANSACT = BigInteger.TEN.pow(18 + 5);
    private static BigInteger MIN_SELF_STAKE = new BigInteger("1000000000000000000000");
    private static long COMMISSION_RATE_CHANGE_TIME_LOCK_PERIOD = 6 * 60 * 24 * 7;

    @Rule
    public AvmRule RULE = new AvmRule(false);

    // default address with balance
    private Address preminedAddress;

    // contract address
    private Address stakerRegistry;
    private Address poolRegistry;

    @Before
    public void setup() {
        preminedAddress = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        try (Scanner s = new Scanner(PoolRegistryTest.class.getResourceAsStream("StakerRegistry.txt"))) {
            String contract = s.nextLine();
            AvmRule.ResultWrapper result = RULE.deploy(preminedAddress, BigInteger.ZERO, Hex.decode(contract));
            Assert.assertTrue(result.getReceiptStatus().isSuccess());
            stakerRegistry = result.getDappAddress();
        }

        Address placeHolder = new Address(Helpers.hexStringToBytes("0000000000000000000000000000000000000000000000000000000000000000"));
        byte[] coinbaseArguments = ABIUtil.encodeDeploymentArguments(placeHolder);
        byte[] coinbaseBytes = RULE.getDappBytes(PoolCoinbase.class, coinbaseArguments, 1);

        byte[] arguments = ABIUtil.encodeDeploymentArguments(stakerRegistry, MIN_SELF_STAKE, BigInteger.ONE, COMMISSION_RATE_CHANGE_TIME_LOCK_PERIOD, coinbaseBytes);
        byte[] data = RULE.getDappBytes(PoolRegistry.class, arguments, 1, PoolStorageObjects.class, PoolRewardsStateMachine.class, PoolRegistryEvents.class, PoolRegistryStorage.class);

        AvmRule.ResultWrapper result = RULE.deploy(preminedAddress, BigInteger.ZERO, data);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        Assert.assertEquals(1, result.getLogs().size());
        Log poolRegistryEvent = result.getLogs().get(0);
        Assert.assertArrayEquals(LogSizeUtils.truncatePadTopic("ADSDeployed".getBytes()), poolRegistryEvent.copyOfTopics().get(0));
        Assert.assertArrayEquals(stakerRegistry.toByteArray(), poolRegistryEvent.copyOfTopics().get(1));
        Assert.assertEquals(MIN_SELF_STAKE, new BigInteger(poolRegistryEvent.copyOfTopics().get(2)));
        Assert.assertEquals(BigInteger.ONE, new BigInteger(poolRegistryEvent.copyOfTopics().get(3)));
        Assert.assertEquals(COMMISSION_RATE_CHANGE_TIME_LOCK_PERIOD, new BigInteger(poolRegistryEvent.copyOfData()).longValue());

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
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(true, result.getDecodedReturnData());

        return newPool;
    }

    @Test
    public void reentrantAutoDelegateRewards() {
        Address pool = setupNewPool(0);
        BigInteger stake = nStake(1);

        // deploy contract
        byte[] arguments = ABIUtil.encodeDeploymentArguments(poolRegistry, pool);
        byte[] data = RULE.getDappBytes(ReentrantContract.class, arguments, 1);

        AvmRule.ResultWrapper result = RULE.deploy(preminedAddress, BigInteger.ZERO, data);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Address reentractContract = result.getDappAddress();

        // delegate using the contract
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .toBytes();
        result = RULE.call(preminedAddress, reentractContract, stake, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("enableAutoRewardsDelegation")
                .toBytes();
        result = RULE.call(preminedAddress, reentractContract, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        generateBlock(pool, 1000000000);

        txData = new ABIStreamingEncoder()
                .encodeOneString("autoDelegateRewards")
                .toBytes();
        result = RULE.call(preminedAddress, reentractContract, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(pool)
                .encodeOneAddress(reentractContract)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        BigInteger currentStake = (BigInteger) result.getDecodedReturnData();
        BigInteger rewards = BigInteger.valueOf(1000000000 / 2);
        BigInteger fee = rewards.divide(BigInteger.valueOf(1000000));
        // stake = value of stake + half of the rewards - fee
        // if the re-entrant call could cheat the system, this value would be greater
        Assert.assertEquals(stake.add(rewards).subtract(fee), currentStake);

        Assert.assertEquals(fee, RULE.kernel.getBalance(new AionAddress(reentractContract.toByteArray())));
    }

    @Test
    public void reentrantGetRewards() {
        Address pool = setupNewPool(0);
        BigInteger stake = nStake(1);

        // deploy contract
        byte[] arguments = ABIUtil.encodeDeploymentArguments(poolRegistry, pool);
        byte[] data = RULE.getDappBytes(ReentrantContract.class, arguments, 1);

        AvmRule.ResultWrapper result = RULE.deploy(preminedAddress, BigInteger.ZERO, data);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Address reentractContract = result.getDappAddress();

        // delegate using the contract
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .toBytes();
        result = RULE.call(preminedAddress, reentractContract, stake, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        generateBlock(pool, 1000000000);

        txData = new ABIStreamingEncoder()
                .encodeOneString("withdrawRewards")
                .toBytes();
        result = RULE.call(preminedAddress, reentractContract, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // total rewards/2
        // if the re-entrant call could cheat the system, this value would be greater
        Assert.assertEquals(1000000000 / 2, RULE.kernel.getBalance(new AionAddress(reentractContract.toByteArray())).intValue());
    }

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

    private Address getCoinbaseAddress(Address pool) {
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("getCoinbaseAddress")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        return (Address) result.getDecodedReturnData();
    }
}
