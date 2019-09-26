package org.aion.unity;

import avm.Address;
import org.aion.avm.core.util.LogSizeUtils;
import org.aion.avm.embed.AvmRule;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;
import org.aion.kernel.TestingState;
import org.aion.types.Log;
import org.junit.*;

import java.lang.reflect.Field;
import java.math.BigInteger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;


public class StakerRegistryTest {

    private static BigInteger ENOUGH_BALANCE_TO_TRANSACT = BigInteger.TEN.pow(18 + 5);

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

        // deploy the staker registry contract
        byte[] jar = RULE.getDappBytes(StakerRegistry.class, null, 1, otherClasses);
        stakerRegistry = RULE.deploy(preminedAddress, BigInteger.ZERO, jar).getDappAddress();

        // register the staker
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("registerStaker")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(signingAddress)
                .encodeOneAddress(coinbaseAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, StakerRegistry.MIN_SELF_STAKE, txData);
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
    public void testVoteAndUnvote() {
        BigInteger voteAmount = BigInteger.valueOf(1000L);
        BigInteger unvoteAmount = BigInteger.valueOf(900L);

        // delegate first
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, voteAmount, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // then undelegate
        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(stakerAddress)
                .encodeOneBigInteger(unvoteAmount)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        long id = (long) result.getDecodedReturnData();

        assertEquals(1, result.getLogs().size());
        Log poolRegistryEvent = result.getLogs().get(0);
        assertArrayEquals(LogSizeUtils.truncatePadTopic("Undelegated".getBytes()),
                poolRegistryEvent.copyOfTopics().get(0));
        assertEquals(id, new BigInteger(poolRegistryEvent.copyOfTopics().get(1)).longValue());
        assertArrayEquals(stakerAddress.toByteArray(), poolRegistryEvent.copyOfTopics().get(2));
        assertArrayEquals(stakerAddress.toByteArray(), poolRegistryEvent.copyOfTopics().get(3));
        ABIDecoder decoder = new ABIDecoder(poolRegistryEvent.copyOfData());
        assertEquals(unvoteAmount, decoder.decodeOneBigInteger());
        assertEquals(BigInteger.ZERO, decoder.decodeOneBigInteger());

        // query the total stake of staker
        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(StakerRegistry.MIN_SELF_STAKE.add(voteAmount.subtract(unvoteAmount)), result.getDecodedReturnData());
    }

    @Test
    public void testVoteAndUnvoteAll() {
        BigInteger voteAmount = BigInteger.valueOf(1000L);

        // delegate first
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, voteAmount, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // query the total stake of staker
        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(StakerRegistry.MIN_SELF_STAKE.add(voteAmount), result.getDecodedReturnData());

        // then undelegate
        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(stakerAddress)
                .encodeOneBigInteger(voteAmount)
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
        Assert.assertEquals(StakerRegistry.MIN_SELF_STAKE, result.getDecodedReturnData());
    }

    @Test
    public void testVoteUnvoteTransfer() {
        BigInteger voteAmount = BigInteger.valueOf(1000L);

        // delegate first
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, voteAmount, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // query the stake
        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(StakerRegistry.MIN_SELF_STAKE.add(voteAmount), result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, voteAmount, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(StakerRegistry.MIN_SELF_STAKE.add(voteAmount.multiply(BigInteger.TWO)), result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, voteAmount, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // then undelegate
        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(stakerAddress)
                .encodeOneBigInteger(voteAmount)
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
        Assert.assertEquals(StakerRegistry.MIN_SELF_STAKE.add(voteAmount.multiply(BigInteger.TWO)), result.getDecodedReturnData());

        Address stakerAddress2 = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

       txData = new ABIStreamingEncoder()
                .encodeOneString("registerStaker")
                .encodeOneAddress(stakerAddress2)
                .encodeOneAddress(stakerAddress2)
                .encodeOneAddress(stakerAddress2)
                .encodeOneAddress(stakerAddress2)
                .toBytes();
        result = RULE.call(stakerAddress2, stakerRegistry, StakerRegistry.MIN_SELF_STAKE, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(stakerAddress2)
                .encodeOneBigInteger(voteAmount)
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
        Assert.assertEquals(StakerRegistry.MIN_SELF_STAKE.add(voteAmount), result.getDecodedReturnData());
    }

    @Test
    public void testGetEffectiveStake() {
        BigInteger halfMinStake = StakerRegistry.MIN_SELF_STAKE.divide(BigInteger.TWO);

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
        result = RULE.call(stakerAddress, stakerRegistry, StakerRegistry.MIN_SELF_STAKE, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // query the effective stake again
        txData = new ABIStreamingEncoder()
                .encodeOneString("getEffectiveStake")
                .encodeOneAddress(signingAddress)
                .encodeOneAddress(coinbaseAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(StakerRegistry.MIN_SELF_STAKE.add(halfMinStake), result.getDecodedReturnData());

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
                .encodeOneBigInteger(StakerRegistry.MIN_SELF_STAKE)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // voting for stakerAddress does not go into the self bond stake
        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, StakerRegistry.MIN_SELF_STAKE, txData);
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
        result = RULE.call(stakerAddress, stakerRegistry, StakerRegistry.MIN_SELF_STAKE, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // query the effective stake again
        txData = new ABIStreamingEncoder()
                .encodeOneString("getEffectiveStake")
                .encodeOneAddress(signingAddress)
                .encodeOneAddress(coinbaseAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(StakerRegistry.MIN_SELF_STAKE.multiply(BigInteger.TWO), result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getSelfBondStake")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(StakerRegistry.MIN_SELF_STAKE, result.getDecodedReturnData());
    }

    @Test
    public void testTransferStake() {
        BigInteger voteAmount = BigInteger.valueOf(1000L);
        BigInteger transferAmount = BigInteger.valueOf(100L);
        Address stakerAddress2 = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);
        Address signingAddress2 = RULE.getRandomAddress(BigInteger.ZERO);
        Address coinbaseAddress2 = RULE.getRandomAddress(BigInteger.ZERO);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("registerStaker")
                .encodeOneAddress(stakerAddress2)
                .encodeOneAddress(stakerAddress2)
                .encodeOneAddress(signingAddress2)
                .encodeOneAddress(coinbaseAddress2)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress2, stakerRegistry, StakerRegistry.MIN_SELF_STAKE, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // delegate first
        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, voteAmount, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(stakerAddress2)
                .encodeOneBigInteger(transferAmount)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        long id = (long) result.getDecodedReturnData();
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // tweak the block number to skip the TRANSFER_LOCK_UP_PERIOD
        tweakBlockNumber(RULE.kernel.getBlockNumber() + StakerRegistry.TRANSFER_LOCK_UP_PERIOD);

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
        Assert.assertEquals(StakerRegistry.MIN_SELF_STAKE.add(voteAmount.subtract(transferAmount)), result.getDecodedReturnData());

        // query the stake to the other staker
        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(stakerAddress2)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(StakerRegistry.MIN_SELF_STAKE.add(transferAmount), result.getDecodedReturnData());

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

        tweakBlockNumber(RULE.kernel.getBlockNumber() + StakerRegistry.SIGNING_ADDRESS_COOLING_PERIOD);

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
    }

    @Test
    public void testLockupPeriod() {
        BigInteger voteAmount = BigInteger.valueOf(1000L);
        BigInteger unvoteAmount = BigInteger.valueOf(900L);

        // delegate first
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, voteAmount, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // then undelegate
        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(stakerAddress)
                .encodeOneBigInteger(unvoteAmount)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        long id = (long) result.getDecodedReturnData();
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        long blockNumber = RULE.kernel.getBlockNumber();

        // now try to release
        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeUndelegate")
                .encodeOneLong(id)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertFalse(result.getReceiptStatus().isSuccess());

        // tweak the block number
        tweakBlockNumber(blockNumber + StakerRegistry.UNDELEGATE_LOCK_UP_PERIOD);

        // and, query again
        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeUndelegate")
                .encodeOneLong(id)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
    }

    @Test
    public void testUpdateSigningAddress() {
        Address anotherSigningAddress = RULE.getRandomAddress(BigInteger.ZERO);
        Address anotherCoinbaseAddress = RULE.getRandomAddress(BigInteger.ZERO);

        tweakBlockNumber(RULE.kernel.getBlockNumber() + StakerRegistry.SIGNING_ADDRESS_COOLING_PERIOD);

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
                .encodeOneAddress(newStakerAddress)
                .encodeOneAddress(signingAddress)
                .encodeOneAddress(anotherCoinbaseAddress)
                .toBytes();

        result = RULE.call(newStakerAddress, stakerRegistry, StakerRegistry.MIN_SELF_STAKE, txData);
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
        // test delegate can only be called by the management address
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, stakerRegistry, BigInteger.TEN, txData);
        Assert.assertTrue(result.getReceiptStatus().isFailed());

        // do delegate to ensure undelegate fails due to incorrect caller
        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.TEN, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // test undelegate can only be called by the management address
        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
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
                .encodeOneAddress(newStakerAddress)
                .encodeOneAddress(newSigningAddress)
                .encodeOneAddress(newSigningAddress)
                .toBytes();
        RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);

        // test transferDelegation can only be called by the management address
        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(newStakerAddress)
                .encodeOneBigInteger(BigInteger.ONE)
                .encodeOneBigInteger(BigInteger.ZERO)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isFailed());
    }

    @Test
    public void testRegisterStakerWithDuplicateValues(){
        Address newAddress = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        // already used signing address
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("registerStaker")
                .encodeOneAddress(newAddress)
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
