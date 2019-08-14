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
import org.spongycastle.util.encoders.Hex;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.math.BigInteger;

import static org.junit.Assert.assertEquals;


public class SlashingTest {

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
        byte[] jar = RULE.getDappBytes(StakerRegistry.class, null, AionBlockHeader.class, RlpDecoder.class, RlpEncoder.class, RlpList.class, RlpString.class, RlpType.class, Arrays.class);
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
    public void testSlash() {
        int type = 1;
        byte[] header = Hex.decode("f8dd0102a06068a128093216a3c0bd9b8cecea132731b8d4fca67de87020b42965fd32a581a0bee628af072dde474c426e5062b2c5ad6888ad3221fe949a1962df918159ded2a0f2953aeb18a5bcb88220ecc0f2c5e222d932f09cc7a26f724276c67fb1c301a5a02e5def03d03e5e6a1b2b22c8185263920b36e056d4e7b1a0d9318764ce0758f3a090685796aa5ee0da1a7d27b5d43b7d52e9f8f2199f5d579489431524170d9828a00000000000000000000000000000000000000000000000000000000000000000038464617461040506857061727431857061727432");
        byte[][] headers = {header};

        // do a self-bond
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("vote")
                .encodeOneAddress(stakerAddress)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(stakerAddress, stakerRegistry, StakerRegistry.MIN_SELF_STAKE, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // submit a proof
        txData = new ABIStreamingEncoder()
                .encodeOneString("slash")
                .encodeOneInteger(type)
                .encodeOne2DByteArray(headers)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        // query the stake
        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(stakerAddress)
                .encodeOneAddress(stakerAddress)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(StakerRegistry.MIN_SELF_STAKE.min(StakerRegistry.SLASHING_AMOUNT).longValue(), result.getDecodedReturnData());
    }

}
