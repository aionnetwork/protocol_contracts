package org.aion.unity;

import avm.Address;
import org.aion.avm.embed.AvmRule;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;
import org.aion.kernel.TestingState;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.lang.reflect.Field;
import java.math.BigInteger;


public class StakerRegistryTest {

    private static BigInteger ENOUGH_BALANCE_TO_TRANSACT = BigInteger.TEN.pow(18 + 5);

    @Rule
    public AvmRule RULE = new AvmRule(false);

    private Address preminedAddress;

    private Address stakerAddress;
    private Address signingAddress;
    private Address coinbaseAddress;

    private Class[] otherClasses = {StakerRegistryEvents.class};
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
                .encodeOneAddress(stakerAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
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
                .encodeOneString("getCoinbaseAddressForIdentityAddress")
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
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, stakerRegistry, voteAmount, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // then undelegate
        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(stakerAddress)
                .encodeOneBigInteger(unvoteAmount)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // query the stake
        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(preminedAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(voteAmount.subtract(unvoteAmount), result.getDecodedReturnData());


        // query the total stake of staker
        txData = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(voteAmount.subtract(unvoteAmount), result.getDecodedReturnData());
    }

    @Test
    public void testGetEffectiveStake() {
        BigInteger halfMinStake = StakerRegistry.MIN_SELF_STAKE.divide(BigInteger.valueOf(2));

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("bond")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, halfMinStake, txData);
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
        result = RULE.call(stakerAddress, stakerRegistry, halfMinStake, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // query the effective stake again
        txData = new ABIStreamingEncoder()
                .encodeOneString("getEffectiveStake")
                .encodeOneAddress(signingAddress)
                .encodeOneAddress(coinbaseAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(StakerRegistry.MIN_SELF_STAKE, result.getDecodedReturnData());
    }

    @Test
    public void testBond() {
        // voting for stakerAddress does not go into the self bond stake
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, StakerRegistry.MIN_SELF_STAKE, txData);
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
                .encodeOneAddress(stakerAddress2)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress2, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // delegate first
        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, voteAmount, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(stakerAddress2)
                .encodeOneBigInteger(transferAmount)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        long id = (long) result.getDecodedReturnData();
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // tweak the block number to skip the TRANSFER_LOCK_UP_PERIOD
        tweakBlockNumber(RULE.kernel.getBlockNumber() + StakerRegistry.TRANSFER_LOCK_UP_PERIOD);

        // the recipient staker needs to finalize the transfer
        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeTransfer")
                .encodeOneLong(id)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // query the stake to the first staker
        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(preminedAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(voteAmount.subtract(transferAmount), result.getDecodedReturnData());

        // query the stake to the other staker
        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(stakerAddress2)
                .encodeOneAddress(preminedAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(transferAmount, result.getDecodedReturnData());
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
                .encodeOneString("getCoinbaseAddressForIdentityAddress")
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
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, stakerRegistry, voteAmount, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // then undelegate
        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(stakerAddress)
                .encodeOneBigInteger(unvoteAmount)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
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
    public void testGetPendingUnvoteIds() {
        BigInteger voteAmount = BigInteger.valueOf(1000L);
        BigInteger unvoteAmount = BigInteger.valueOf(90L);;

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, stakerRegistry, voteAmount, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getPendingUndelegateIds")
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertArrayEquals(new long[]{}, (long[]) result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(stakerAddress)
                .encodeOneBigInteger(unvoteAmount)
                .toBytes();
        int size = 10;
        long[] ids = new long[size];
        for (int i = 0; i < size; i++) {
            result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
            Assert.assertTrue(result.getReceiptStatus().isSuccess());
            ids[i] = i;
        }

        long blockNumber = RULE.kernel.getBlockNumber();

        txData = new ABIStreamingEncoder()
                .encodeOneString("getPendingUndelegateIds")
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertArrayEquals(ids, (long[]) result.getDecodedReturnData());

        tweakBlockNumber(blockNumber + StakerRegistry.UNDELEGATE_LOCK_UP_PERIOD);

        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeUndelegate")
                .encodeOneLong(0)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getPendingUndelegateIds")
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertArrayEquals(new long[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, (long[]) result.getDecodedReturnData());

        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeUndelegate")
                .encodeOneLong(5)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getPendingUndelegateIds")
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertArrayEquals(new long[]{1, 2, 3, 4, 6, 7, 8, 9}, (long[]) result.getDecodedReturnData());
    }

    @Test
    public void testGetPendingTransferIds() {
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
                .encodeOneAddress(stakerAddress2)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress2, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, voteAmount, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("transferDelegation")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(stakerAddress2)
                .encodeOneBigInteger(transferAmount)
                .toBytes();
        int size = 10;
        long[] ids = new long[size];
        for (int i = 0; i < size; i++) {
            result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
            Assert.assertTrue(result.getReceiptStatus().isSuccess());
            ids[i] = i;
        }

        txData = new ABIStreamingEncoder()
                .encodeOneString("getPendingTransferIds")
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertArrayEquals(ids, (long[]) result.getDecodedReturnData());

        // tweak the block number to skip the TRANSFER_LOCK_UP_PERIOD
        tweakBlockNumber(RULE.kernel.getBlockNumber() + StakerRegistry.TRANSFER_LOCK_UP_PERIOD);

        // the recipient staker needs to finalize the transfer
        txData = new ABIStreamingEncoder()
                .encodeOneString("finalizeTransfer")
                .encodeOneLong(9)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getPendingTransferIds")
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertArrayEquals(new long[]{0, 1, 2, 3, 4, 5, 6, 7, 8}, (long[]) result.getDecodedReturnData());
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
                .encodeOneString("getCoinbaseAddressForSigningAddress")
                .encodeOneAddress(signingAddress)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertFalse(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getCoinbaseAddressForSigningAddress")
                .encodeOneAddress(anotherSigningAddress)
                .toBytes();
        result = RULE.call(stakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(coinbaseAddress, result.getDecodedReturnData());

        Address newStakerAddress = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);

        txData = new ABIStreamingEncoder()
                .encodeOneString("registerStaker")
                .encodeOneAddress(newStakerAddress)
                .encodeOneAddress(newStakerAddress)
                .encodeOneAddress(signingAddress)
                .encodeOneAddress(anotherCoinbaseAddress)
                .encodeOneAddress(newStakerAddress)
                .toBytes();

        result = RULE.call(newStakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getCoinbaseAddressForSigningAddress")
                .encodeOneAddress(signingAddress)
                .toBytes();
        result = RULE.call(newStakerAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        Assert.assertEquals(anotherCoinbaseAddress, result.getDecodedReturnData());
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
