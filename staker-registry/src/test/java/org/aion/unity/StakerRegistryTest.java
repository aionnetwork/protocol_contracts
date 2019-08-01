package org.aion.unity;

import avm.Address;
import org.aion.avm.tooling.AvmRule;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;
import org.aion.kernel.TestingKernel;
import org.aion.vm.api.interfaces.ResultCode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.lang.reflect.Field;
import java.math.BigInteger;


public class StakerRegistryTest {

    private static BigInteger ENOUGH_BALANCE_TO_TRANSACT = BigInteger.TEN.pow(18 + 5);

    @Rule
    public AvmRule RULE = new AvmRule(true);

    private Address preminedAddress;

    private Address stakerAddress;
    private Address signingAddress;
    private Address coinbaseAddress;

    private Address stakerRegistry;

    @Before
    public void setup() {
        // setup accounts
        preminedAddress = RULE.getPreminedAccount();

        stakerAddress = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);
        signingAddress = RULE.getRandomAddress(BigInteger.ZERO);
        coinbaseAddress = RULE.getRandomAddress(BigInteger.ZERO);

        // deploy the staker registry contract
        byte[] jar = RULE.getDappBytes(StakerRegistry.class, null);
        stakerRegistry = RULE.deploy(preminedAddress, BigInteger.ZERO, jar).getDappAddress();

        // register the staker
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("registerStaker")
                .encodeOneAddress(signingAddress)
                .encodeOneAddress(coinbaseAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        ResultCode status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
    }

    @Test
    public void testRegisterStaker() {
        // query the signing address
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("getSigningAddress")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        ResultCode status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
        Assert.assertEquals(signingAddress, result.getDecodedReturnData());

        // query the coinbase address
        txData = new ABIStreamingEncoder()
                .encodeOneString("getCoinbaseAddress")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
        Assert.assertEquals(coinbaseAddress, result.getDecodedReturnData());
    }

    @Test
    public void testVoteAndUnvote() {
        long voteAmount = 1000L;
        long unvoteAmount = 900L;

        // vote first
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("vote")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, stakerRegistry, BigInteger.valueOf(voteAmount), txData);
        ResultCode status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());

        // then unvote
        txData = new ABIStreamingEncoder()
                .encodeOneString("unvote")
                .encodeOneAddress(stakerAddress)
                .encodeOneLong(unvoteAmount)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());

        // query the stake
        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(preminedAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
        Assert.assertEquals(voteAmount - unvoteAmount, result.getDecodedReturnData());


        // query the total stake of staker
        txData = new ABIStreamingEncoder()
                .encodeOneString("getStakeByStakerAddress")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
        Assert.assertEquals(voteAmount - unvoteAmount, result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getStakeBySigningAddress")
                .encodeOneAddress(signingAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
        Assert.assertEquals(voteAmount - unvoteAmount, result.getDecodedReturnData());
    }

    @Test
    public void testTransferStake() {
        long voteAmount = 1000L;
        long transferAmount = 100L;
        Address stakerAddress2 = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);
        Address signingAddress2 = RULE.getRandomAddress(BigInteger.ZERO);
        Address coinbaseAddress2 = RULE.getRandomAddress(BigInteger.ZERO);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("registerStaker")
                .encodeOneAddress(signingAddress2)
                .encodeOneAddress(coinbaseAddress2)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress2, stakerRegistry, BigInteger.ZERO, txData);
        ResultCode status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());

        // vote first
        txData = new ABIStreamingEncoder()
                .encodeOneString("vote")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.valueOf(voteAmount), txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());

        // then unvote
        txData = new ABIStreamingEncoder()
                .encodeOneString("transferStake")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(stakerAddress2)
                .encodeOneLong(transferAmount)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());

        // query the stake to the first staker
        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(preminedAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
        Assert.assertEquals(voteAmount - transferAmount, result.getDecodedReturnData());

        // query the stake to the other staker
        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(stakerAddress2)
                .encodeOneAddress(preminedAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
        Assert.assertEquals(transferAmount, result.getDecodedReturnData());
    }

    @Test
    public void testSetSigningAddress() {
        Address anotherAddress = RULE.getRandomAddress(BigInteger.ZERO);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("setSigningAddress")
                .encodeOneAddress(anotherAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        ResultCode status = result.getReceiptStatus();
        Assert.assertFalse(status.isSuccess());

        tweakBlockNumber(1L + StakerRegistry.ADDRESS_UPDATE_COOL_DOWN_PERIOD);

        txData = new ABIStreamingEncoder()
                .encodeOneString("setSigningAddress")
                .encodeOneAddress(anotherAddress)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getSigningAddress")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
        Assert.assertEquals(anotherAddress, result.getDecodedReturnData());
    }

    @Test
    public void testSetCoinbaseAddress() {
        Address anotherAddress = RULE.getRandomAddress(BigInteger.ZERO);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("setCoinbaseAddress")
                .encodeOneAddress(anotherAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        ResultCode status = result.getReceiptStatus();
        Assert.assertFalse(status.isSuccess());

        tweakBlockNumber(1L + StakerRegistry.ADDRESS_UPDATE_COOL_DOWN_PERIOD);

        txData = new ABIStreamingEncoder()
                .encodeOneString("setCoinbaseAddress")
                .encodeOneAddress(anotherAddress)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());


        txData = new ABIStreamingEncoder()
                .encodeOneString("getCoinbaseAddress")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
        Assert.assertEquals(anotherAddress, result.getDecodedReturnData());
    }

    @Test
    public void testListener() {

    }

    @Test
    public void testLockupPeriod() {
        long voteAmount = 1000L;
        long unvoteAmount = 900L;

        // vote first
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("vote")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, stakerRegistry, BigInteger.valueOf(voteAmount), txData);
        ResultCode status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());

        // then unvote
        txData = new ABIStreamingEncoder()
                .encodeOneString("unvote")
                .encodeOneAddress(stakerAddress)
                .encodeOneLong(unvoteAmount)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());


        // now try to release
        txData = new ABIStreamingEncoder()
                .encodeOneString("releaseStake")
                .encodeOneAddress(preminedAddress)
                .encodeOneInteger(100)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
        Assert.assertEquals(0, result.getDecodedReturnData());

        // tweak the block number
        tweakBlockNumber(1L + StakerRegistry.STAKE_LOCK_UP_PERIOD);

        // and, query again
        txData = new ABIStreamingEncoder()
                .encodeOneString("releaseStake")
                .encodeOneAddress(preminedAddress)
                .encodeOneInteger(100)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
        Assert.assertEquals(1, result.getDecodedReturnData());
    }

    public void tweakBlockNumber(long number) {
        try {
            Field f = TestingKernel.class.getDeclaredField("blockNumber");
            f.setAccessible(true);

            f.set(RULE.kernel, number);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
