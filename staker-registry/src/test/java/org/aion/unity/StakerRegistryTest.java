package org.aion.unity;

import avm.Address;
import org.aion.avm.core.util.LogSizeUtils;
import org.aion.avm.embed.AvmRule;
import org.aion.avm.tooling.ABIUtil;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;
import org.aion.kernel.TestingState;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.junit.*;

import java.lang.reflect.Field;
import java.math.BigInteger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;


public class StakerRegistryTest {

    private static BigInteger ENOUGH_BALANCE_TO_TRANSACT = BigInteger.TEN.pow(18 + 5);
    private static final BigInteger MIN_SELF_STAKE = new BigInteger("1000000000000000000000");
    private static final long SIGNING_ADDRESS_COOLING_PERIOD = 6 * 60 * 24 * 7;
    private static final long unbond_LOCK_UP_PERIOD = 6 * 60 * 24;
    private static final long TRANSFER_LOCK_UP_PERIOD = 6 * 10;
    @Rule
    public AvmRule RULE = new AvmRule(false);

    private Address preminedAddress;

    private Address stakerAddress;
    private Address signingAddress;
    private Address coinbaseAddress;

    private Class[] otherClasses = {StakerRegistryEvents.class, StakerStorageObjects.class, StakerRegistryStorage.class};
    private Address stakerRegistry;

    @Before
    public void setup() {
        // setup accounts
        preminedAddress = RULE.getPreminedAccount();

        stakerAddress = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);
        signingAddress = RULE.getRandomAddress(BigInteger.ZERO);
        coinbaseAddress = RULE.getRandomAddress(BigInteger.ZERO);

        byte[] arguments = ABIUtil.encodeDeploymentArguments(MIN_SELF_STAKE, SIGNING_ADDRESS_COOLING_PERIOD, unbond_LOCK_UP_PERIOD, TRANSFER_LOCK_UP_PERIOD);

        // deploy the staker registry contract
        byte[] jar = RULE.getDappBytes(StakerRegistry.class, arguments, 1, otherClasses);
        AvmRule.ResultWrapper result = RULE.deploy(preminedAddress, BigInteger.ZERO, jar);
        stakerRegistry = result.getDappAddress();

        assertEquals(1, result.getLogs().size());
        Log stakerRegistryEvent = result.getLogs().get(0);
        assertArrayEquals(LogSizeUtils.truncatePadTopic("StakerRegistryDeployed".getBytes()), stakerRegistryEvent.copyOfTopics().get(0));
        assertEquals(MIN_SELF_STAKE,  new BigInteger(stakerRegistryEvent.copyOfTopics().get(1)));
        assertEquals(SIGNING_ADDRESS_COOLING_PERIOD, new BigInteger(stakerRegistryEvent.copyOfTopics().get(2)).longValue());
        assertEquals(unbond_LOCK_UP_PERIOD, new BigInteger(stakerRegistryEvent.copyOfTopics().get(3)).longValue());
        assertEquals(TRANSFER_LOCK_UP_PERIOD, new BigInteger(stakerRegistryEvent.copyOfData()).longValue());

        // register the staker
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("registerStaker")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(signingAddress)
                .encodeOneAddress(coinbaseAddress)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, MIN_SELF_STAKE, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
    }

    @Test
    public void testRegisterStaker() {
        // query the signing address
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("getSigningAddress")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(signingAddress, result.getDecodedReturnData());

        // query the coinbase address
        txData = new ABIStreamingEncoder()
                .encodeOneString("getCoinbaseAddress")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(coinbaseAddress, result.getDecodedReturnData());
    }

    @Test
    public void testBondAndUnbond() {
        BigInteger bondAmount = BigInteger.valueOf(1000L);
        BigInteger unbondAmount = BigInteger.valueOf(900L);

        // bond first
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("bond")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, bondAmount, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // then unbond
        txData = new ABIStreamingEncoder()
                .encodeOneString("unbond")
                .encodeOneAddress(stakerAddress)
                .encodeOneBigInteger(unbondAmount)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        long id = (long) result.getDecodedReturnData();

        assertEquals(1, result.getLogs().size());
        Log poolRegistryEvent = result.getLogs().get(0);
        assertArrayEquals(LogSizeUtils.truncatePadTopic("Unbonded".getBytes()),
                poolRegistryEvent.copyOfTopics().get(0));
        assertEquals(id, new BigInteger(poolRegistryEvent.copyOfTopics().get(1)).longValue());
        assertArrayEquals(stakerAddress.toByteArray(), poolRegistryEvent.copyOfTopics().get(2));
        assertArrayEquals(stakerAddress.toByteArray(), poolRegistryEvent.copyOfTopics().get(3));
        ABIDecoder decoder = new ABIDecoder(poolRegistryEvent.copyOfData());
        assertEquals(unbondAmount, decoder.decodeOneBigInteger());
        assertEquals(BigInteger.ZERO, decoder.decodeOneBigInteger());

        // query the total stake of staker
        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(MIN_SELF_STAKE.add(bondAmount.subtract(unbondAmount)), result.getDecodedReturnData());
    }

    @Test
    public void testBondAndUnbondAll() {
        BigInteger bondAmount = BigInteger.valueOf(1000L);

        // bond first
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("bond")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, bondAmount, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // query the total stake of staker
        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(MIN_SELF_STAKE.add(bondAmount), result.getDecodedReturnData());

        // then unbond
        txData = new ABIStreamingEncoder()
                .encodeOneString("unbond")
                .encodeOneAddress(stakerAddress)
                .encodeOneBigInteger(bondAmount)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // query the total stake of staker
        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(MIN_SELF_STAKE, result.getDecodedReturnData());
    }

    @Test
    public void testBondUnbondTransfer() {
        BigInteger bondAmount = BigInteger.valueOf(1000L);

        // bond first
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("bond")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, bondAmount, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // query the stake
        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(MIN_SELF_STAKE.add(bondAmount), result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("bond")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, bondAmount, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(MIN_SELF_STAKE.add(bondAmount.multiply(BigInteger.TWO)), result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("bond")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, bondAmount, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // then unbond
        txData = new ABIStreamingEncoder()
                .encodeOneString("unbond")
                .encodeOneAddress(stakerAddress)
                .encodeOneBigInteger(bondAmount)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // query the stake
        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(MIN_SELF_STAKE.add(bondAmount.multiply(BigInteger.TWO)), result.getDecodedReturnData());

        Address stakerAddress2 = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

       txData = new ABIStreamingEncoder()
                .encodeOneString("registerStaker")
                .encodeOneAddress(stakerAddress2)
                .encodeOneAddress(stakerAddress2)
                .encodeOneAddress(stakerAddress2)
                .toBytes();
        result = RULE.call(stakerAddress2, stakerRegistry, MIN_SELF_STAKE, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("transferStake")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(stakerAddress2)
                .encodeOneBigInteger(bondAmount)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // query the total stake of staker
        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(MIN_SELF_STAKE.add(bondAmount), result.getDecodedReturnData());
    }

    @Test
    public void testGetEffectiveStake() {
        BigInteger halfMinStake = MIN_SELF_STAKE.divide(BigInteger.TWO);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("unbond")
                .encodeOneAddress(stakerAddress)
                .encodeOneBigInteger(halfMinStake)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // query the effective stake
        txData = new ABIStreamingEncoder()
                .encodeOneString("getEffectiveStake")
                .encodeOneAddress(signingAddress)
                .encodeOneAddress(coinbaseAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.ZERO, result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("bond")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, MIN_SELF_STAKE, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // query the effective stake again
        txData = new ABIStreamingEncoder()
                .encodeOneString("getEffectiveStake")
                .encodeOneAddress(signingAddress)
                .encodeOneAddress(coinbaseAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(MIN_SELF_STAKE.add(halfMinStake), result.getDecodedReturnData());

        // wrong signing address
        txData = new ABIStreamingEncoder()
                .encodeOneString("getEffectiveStake")
                .encodeOneAddress(preminedAddress)
                .encodeOneAddress(coinbaseAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.ZERO, result.getDecodedReturnData());

        // wrong coinbase address
        txData = new ABIStreamingEncoder()
                .encodeOneString("getEffectiveStake")
                .encodeOneAddress(signingAddress)
                .encodeOneAddress(preminedAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.ZERO, result.getDecodedReturnData());
    }

    @Test
    public void testBond() {
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("unbond")
                .encodeOneAddress(stakerAddress)
                .encodeOneBigInteger(MIN_SELF_STAKE)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // query the effective stake
        txData = new ABIStreamingEncoder()
                .encodeOneString("getEffectiveStake")
                .encodeOneAddress(signingAddress)
                .encodeOneAddress(coinbaseAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.ZERO, result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("bond")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, MIN_SELF_STAKE, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // query the effective stake
        txData = new ABIStreamingEncoder()
                .encodeOneString("getEffectiveStake")
                .encodeOneAddress(signingAddress)
                .encodeOneAddress(coinbaseAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(MIN_SELF_STAKE, result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("bond")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, MIN_SELF_STAKE, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // query the effective stake again
        txData = new ABIStreamingEncoder()
                .encodeOneString("getEffectiveStake")
                .encodeOneAddress(signingAddress)
                .encodeOneAddress(coinbaseAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(MIN_SELF_STAKE.multiply(BigInteger.TWO), result.getDecodedReturnData());
    }

    @Test
    public void testTransferStake() {
        BigInteger bondAmount = BigInteger.valueOf(1000L);
        BigInteger transferAmount = BigInteger.valueOf(100L);
        Address stakerAddress2 = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);
        Address signingAddress2 = RULE.getRandomAddress(BigInteger.ZERO);
        Address coinbaseAddress2 = RULE.getRandomAddress(BigInteger.ZERO);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("registerStaker")
                .encodeOneAddress(stakerAddress2)
                .encodeOneAddress(signingAddress2)
                .encodeOneAddress(coinbaseAddress2)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress2, stakerRegistry, MIN_SELF_STAKE, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // bond first
        txData = new ABIStreamingEncoder()
                .encodeOneString("bond")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, bondAmount, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("transferStake")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(stakerAddress2)
                .encodeOneBigInteger(transferAmount)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        long id = (long) result.getDecodedReturnData();
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // tweak the block number to skip the TRANSFER_LOCK_UP_PERIOD
        tweakBlockNumber(RULE.kernel.getBlockNumber() + TRANSFER_LOCK_UP_PERIOD);

        // the recipient staker needs to finalize the transfer
        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeTransfer")
                .encodeOneLong(id)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // query the stake to the first staker
        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(MIN_SELF_STAKE.add(bondAmount.subtract(transferAmount)), result.getDecodedReturnData());

        // query the stake to the other staker
        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(stakerAddress2)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(MIN_SELF_STAKE.add(transferAmount), result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeTransfer")
                .encodeOneLong(id)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isFailed());
    }

    @Test
    public void testSetSigningAddress() {
        Address anotherAddress = RULE.getRandomAddress(BigInteger.ZERO);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("setSigningAddress")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(anotherAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertFalse(result.getReceiptStatus().isSuccess());

        tweakBlockNumber(RULE.kernel.getBlockNumber() + SIGNING_ADDRESS_COOLING_PERIOD);

        txData = new ABIStreamingEncoder()
                .encodeOneString("setSigningAddress")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(anotherAddress)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getSigningAddress")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(anotherAddress, result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("setSigningAddress")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(anotherAddress)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(0, result.getTransactionResult().logs.size());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getSigningAddress")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(anotherAddress, result.getDecodedReturnData());
    }

    @Test
    public void testSetCoinbaseAddress() {
        Address anotherAddress = RULE.getRandomAddress(BigInteger.ZERO);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("setCoinbaseAddress")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(anotherAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getCoinbaseAddress")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(anotherAddress, result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("setCoinbaseAddress")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(anotherAddress)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(0, result.getTransactionResult().logs.size());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getCoinbaseAddress")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(anotherAddress, result.getDecodedReturnData());
    }

    @Test
    public void testLockupPeriod() {
        BigInteger bondAmount = BigInteger.valueOf(1000L);
        BigInteger unbondAmount = BigInteger.valueOf(900L);

        // bond first
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("bond")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, bondAmount, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // then unbond
        txData = new ABIStreamingEncoder()
                .encodeOneString("unbond")
                .encodeOneAddress(stakerAddress)
                .encodeOneBigInteger(unbondAmount)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        long id = (long) result.getDecodedReturnData();
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        long blockNumber = RULE.kernel.getBlockNumber();

        // now try to release
        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeUnbond")
                .encodeOneLong(id)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertFalse(result.getReceiptStatus().isSuccess());

        // tweak the block number
        tweakBlockNumber(blockNumber + unbond_LOCK_UP_PERIOD);

        // and, query again
        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeUnbond")
                .encodeOneLong(id)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
    }

    @Test
    public void testUpdateSigningAddress() {
        Address anotherSigningAddress = RULE.getRandomAddress(BigInteger.ZERO);
        Address anotherCoinbaseAddress = RULE.getRandomAddress(BigInteger.ZERO);

        tweakBlockNumber(RULE.kernel.getBlockNumber() + SIGNING_ADDRESS_COOLING_PERIOD);

        // update the signing address
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("setSigningAddress")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(anotherSigningAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getSigningAddress")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(anotherSigningAddress, result.getDecodedReturnData());

        Address newStakerAddress = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        txData = new ABIStreamingEncoder()
                .encodeOneString("registerStaker")
                .encodeOneAddress(newStakerAddress)
                .encodeOneAddress(signingAddress)
                .encodeOneAddress(anotherCoinbaseAddress)
                .toBytes();

        result = RULE.call(newStakerAddress, stakerRegistry, MIN_SELF_STAKE, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getSigningAddress")
                .encodeOneAddress(newStakerAddress)
                .toBytes();
        result = RULE.call(newStakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(signingAddress, result.getDecodedReturnData());
    }

    @Test
    public void testFailCases(){
        // test bond can only be called by the management address
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("bond")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, stakerRegistry, BigInteger.TEN, txData);
        Assert.assertTrue(result.getReceiptStatus().isFailed());

        // do bond to ensure unbond fails due to incorrect caller
        txData = new ABIStreamingEncoder()
                .encodeOneString("bond")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.TEN, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // test unbond can only be called by the management address
        txData = new ABIStreamingEncoder()
                .encodeOneString("unbond")
                .encodeOneAddress(stakerAddress)
                .encodeOneBigInteger(BigInteger.TEN)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isFailed());

        //register staker to ensure transfer fails due to incorrect caller
        Address newStakerAddress = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);
        Address newSigningAddress = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        txData = new ABIStreamingEncoder()
                .encodeOneString("registerStaker")
                .encodeOneAddress(newStakerAddress)
                .encodeOneAddress(newSigningAddress)
                .encodeOneAddress(newSigningAddress)
                .toBytes();
        RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);

        // test transferStake can only be called by the management address
        txData = new ABIStreamingEncoder()
                .encodeOneString("transferStake")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(newStakerAddress)
                .encodeOneBigInteger(BigInteger.ONE)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isFailed());

        // bond with zero
        txData = new ABIStreamingEncoder()
                .encodeOneString("bond")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isFailed());
    }

    @Test
    public void testRegisterStakerWithDuplicateValues(){
        Address newAddress = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        // already used signing address
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("registerStaker")
                .encodeOneAddress(newAddress)
                .encodeOneAddress(signingAddress)
                .encodeOneAddress(newAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isFailed());

        // already used identity address
        txData = new ABIStreamingEncoder()
                .encodeOneString("registerStaker")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(newAddress)
                .encodeOneAddress(newAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isFailed());
    }

    @Test
    public void setStateTest(){
        //only management address should be able to change the state
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("setState")
                .encodeOneAddress(stakerAddress)
                .encodeOneBoolean(false)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isFailed());

        txData = new ABIStreamingEncoder()
                .encodeOneString("setState")
                .encodeOneAddress(stakerAddress)
                .encodeOneBoolean(false)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
    }

    @Test
    public void testUnbondGetEffectiveStake() {
        BigInteger unbondAmount = BigInteger.valueOf(900L);

        // unbond
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("unbond")
                .encodeOneAddress(stakerAddress)
                .encodeOneBigInteger(unbondAmount)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        long id = (long) result.getDecodedReturnData();
        long blockNumber = RULE.kernel.getBlockNumber();

        // query the total effective stake of staker
        txData = new ABIStreamingEncoder()
                .encodeOneString("getEffectiveStake")
                .encodeOneAddress(signingAddress)
                .encodeOneAddress(coinbaseAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.ZERO, result.getDecodedReturnData());

        // tweak the block number
        tweakBlockNumber(blockNumber + unbond_LOCK_UP_PERIOD);

        // and, query again
        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeUnbond")
                .encodeOneLong(id)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getEffectiveStake")
                .encodeOneAddress(signingAddress)
                .encodeOneAddress(coinbaseAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(BigInteger.ZERO, result.getDecodedReturnData());
    }

    @Test
    public void testFeeTransfer() {
        // unbond
        BigInteger unbondAmount = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));
        BigInteger unbondFee = BigInteger.valueOf(10).multiply((BigInteger.TEN).pow(18));

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("unbond")
                .encodeOneAddress(stakerAddress)
                .encodeOneBigInteger(unbondAmount)
                .encodeOneBigInteger(unbondFee)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        long id = (long) result.getDecodedReturnData();

        tweakBlockNumber(RULE.kernel.getBlockNumber() + unbond_LOCK_UP_PERIOD);
        BigInteger preminedBalance = RULE.kernel.getBalance(new AionAddress(preminedAddress.toByteArray()));
        BigInteger stakerBalance = RULE.kernel.getBalance(new AionAddress(stakerAddress.toByteArray()));

        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeUnbond")
                .encodeOneLong(id)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(preminedBalance.add(unbondFee).subtract(BigInteger.valueOf(result.getTransactionResult().energyUsed)),
                RULE.kernel.getBalance(new AionAddress(preminedAddress.toByteArray())));
        Assert.assertEquals(stakerBalance.add(unbondAmount.subtract(unbondFee)),
                RULE.kernel.getBalance(new AionAddress(stakerAddress.toByteArray())));

        // transfer
        BigInteger transferAmount = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));
        BigInteger trasnferFee = BigInteger.valueOf(10).multiply((BigInteger.TEN).pow(18));
        Address stakerAddress2 = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        txData = new ABIStreamingEncoder()
                .encodeOneString("registerStaker")
                .encodeOneAddress(stakerAddress2)
                .encodeOneAddress(stakerAddress2)
                .encodeOneAddress(stakerAddress2)
                .toBytes();
        result = RULE.call(stakerAddress2, stakerRegistry, MIN_SELF_STAKE, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("transferStake")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(stakerAddress2)
                .encodeOneBigInteger(transferAmount)
                .encodeOneBigInteger(trasnferFee)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        id = (long) result.getDecodedReturnData();

        tweakBlockNumber(RULE.kernel.getBlockNumber() + unbond_LOCK_UP_PERIOD);
        stakerBalance = RULE.kernel.getBalance(new AionAddress(stakerAddress.toByteArray()));

        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeTransfer")
                .encodeOneLong(id)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(stakerBalance.add(trasnferFee).subtract(BigInteger.valueOf(result.getTransactionResult().energyUsed)),
                RULE.kernel.getBalance(new AionAddress(stakerAddress.toByteArray())));
    }

    @Test
    public void testFallback(){
        Assert.assertTrue(RULE.balanceTransfer(preminedAddress, stakerRegistry, BigInteger.TEN, 50000L, 1L).getReceiptStatus().isFailed());
    }

    public void tweakBlockNumber(long number) {
        try {
            Field f = TestingState.class.getDeclaredField("blockNumber");
            f.setAccessible(true);

            f.set(RULE.kernel, number);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
