package org.aion.unity;

import avm.Address;
import org.aion.avm.core.util.ABIUtil;
import org.aion.avm.tooling.AvmRule;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;
import org.aion.kernel.TestingKernel;
import org.aion.vm.api.interfaces.ResultCode;
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

public class PoolRegistryTest {

    private static BigInteger ENOUGH_BALANCE_TO_TRANSACT = BigInteger.TEN.pow(18 + 5);

    @Rule
    public AvmRule RULE = new AvmRule(true);

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
        byte[] data = RULE.getDappBytes(PoolRegistry.class, arguments, PoolState.class, PoolRewardsStateMachine.class, Decimal.class);
        AvmRule.ResultWrapper result = RULE.deploy(preminedAddress, BigInteger.ZERO, data);
        assertTrue(result.getReceiptStatus().isSuccess());
        poolRegistry = result.getDappAddress();
    }

    public Address setupNewPool(int fee) {
        Address newPool = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        // STEP-1 register a new staker
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("registerStaker")
                .encodeOneAddress(newPool)
                .encodeOneAddress(newPool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(newPool, stakerRegistry, BigInteger.ZERO, txData);
        ResultCode status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());

        // STEP-2 register a pool
        txData = new ABIStreamingEncoder()
                .encodeOneString("registerPool")
                .encodeOneInteger(fee)
                .encodeOneByteArray("url".getBytes())
                .encodeOneByteArray("contentHash".getBytes())
                .toBytes();
        result = RULE.call(newPool, poolRegistry, BigInteger.ZERO, txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
        Address coinbaseAddress = (Address) result.getDecodedReturnData();

        // STEP-3 set the coinbase address
        txData = new ABIStreamingEncoder()
                .encodeOneString("setCoinbaseAddress")
                .encodeOneAddress(coinbaseAddress)
                .toBytes();
        result = RULE.call(newPool, stakerRegistry, BigInteger.ZERO, txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());

        // STEP-4 update the listener
        txData = new ABIStreamingEncoder()
                .encodeOneString("addListener")
                .encodeOneAddress(poolRegistry)
                .toBytes();
        result = RULE.call(newPool, stakerRegistry, BigInteger.ZERO, txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());

        // STEP-5 do self-stake
        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(newPool)
                .toBytes();
        result = RULE.call(newPool, poolRegistry, nStake(1), txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());

        // verify now
        txData = new ABIStreamingEncoder()
                .encodeOneString("getPoolStatus")
                .encodeOneAddress(newPool)
                .toBytes();
        result = RULE.call(newPool, poolRegistry, BigInteger.ZERO, txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
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
    public void testPoolCoinbaseContract() {
        byte[] arguments = ABIUtil.encodeDeploymentArguments(new Address(new byte[32]));
        byte[] data = RULE.getDappBytes(PoolCoinbase.class, arguments);
        System.out.println(Hex.toHexString(data));
        System.out.println(data.length);
    }


    @Test
    public void testPoolCustodianContract() {
        byte[] arguments = ABIUtil.encodeDeploymentArguments(new Address(new byte[32]), new Address(new byte[32]));
        byte[] data = RULE.getDappBytes(PoolCustodian.class, arguments);
        System.out.println(Hex.toHexString(data));
        System.out.println(data.length);
    }

    @Test
    public void testRegister() {
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("registerPool")
                .encodeOneInteger(5)
                .encodeOneByteArray("url".getBytes())
                .encodeOneByteArray("contentHash".getBytes())
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);

        assertTrue(result.getReceiptStatus().isSuccess());
        assertTrue(result.getDecodedReturnData() instanceof Address);
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
        tweakBlockNumber(1 + 6 * 10);
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
                .encodeOneString("enableAutoRedelegation")
                .encodeOneAddress(pool)
                .encodeOneInteger(20)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        // produce a block
        generateBlock(pool, 100);

        // some third party calls autoRedelegate
        Address random = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);
        txData = new ABIStreamingEncoder()
                .encodeOneString("autoRedelegate")
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
        Long stake = (Long) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 2L, stake.longValue());

        // query the self stake of the pool
        txData = new ABIStreamingEncoder()
                .encodeOneString("getSelfStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(delegator, poolRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        stake = (Long) result.getDecodedReturnData();
        assertEquals(nStake(1).longValue() + 1L, stake.longValue());
    }

    @Test
    public void testTearDownPool() {
        Address pool = setupNewPool(5);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("removeListener")
                .encodeOneAddress(poolRegistry)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(pool, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getPoolStatus")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals("BROKEN", result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("addListener")
                .encodeOneAddress(poolRegistry)
                .toBytes();
        result = RULE.call(pool, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getPoolStatus")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals("ACTIVE", result.getDecodedReturnData());
    }

    @Test
    public void testSlashing() {
        Address pool = setupNewPool(10);

        // do a self-bond
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("vote")
                .encodeOneAddress(pool)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(pool, stakerRegistry, PoolRegistry.MIN_SELF_STAKE, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());


        // [pre-slash] check the pool status
        txData = new ABIStreamingEncoder()
                .encodeOneString("getPoolStatus")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals("ACTIVE", result.getDecodedReturnData());

        // [pre-slash] check the pool stake
        txData = new ABIStreamingEncoder()
                .encodeOneString("getSelfStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(1000L, result.getDecodedReturnData());

        // submit a proof
        int type = 1;
        byte[] header = Hex.decode("f8dd0102a06068a128093216a3c0bd9b8cecea132731b8d4fca67de87020b42965fd32a581a0bee628af072dde474c426e5062b2c5ad6888ad3221fe949a1962df918159ded2a0f2953aeb18a5bcb88220ecc0f2c5e222d932f09cc7a26f724276c67fb1c301a5a02e5def03d03e5e6a1b2b22c8185263920b36e056d4e7b1a0d9318764ce0758f3a090685796aa5ee0da1a7d27b5d43b7d52e9f8f2199f5d579489431524170d9828a00000000000000000000000000000000000000000000000000000000000000000038464617461040506857061727431857061727432");
        byte[][] headers = {header};
        txData = new ABIStreamingEncoder()
                .encodeOneString("slash")
                .encodeOneInteger(type)
                .encodeOne2DByteArray(headers)
                .toBytes();
        // FIMXE: energy usage
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData, 5_000_000L, 1L);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // [post-slash] check the pool status
        txData = new ABIStreamingEncoder()
                .encodeOneString("getPoolStatus")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals("BROKEN", result.getDecodedReturnData());

        // [post-slash] check the pool stake
        txData = new ABIStreamingEncoder()
                .encodeOneString("getSelfStake")
                .encodeOneAddress(pool)
                .toBytes();
        result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(1000L - 100L, result.getDecodedReturnData());
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
                .encodeOneString("getCoinbaseAddress")
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
            Field f = TestingKernel.class.getDeclaredField("blockNumber");
            f.setAccessible(true);

            f.set(RULE.kernel, number);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}

