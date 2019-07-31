package org.aion.unity;

import avm.Address;

import org.aion.avm.core.util.ABIUtil;
import org.aion.avm.tooling.AvmRule;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PoolRegistryTest {

    @ClassRule
    public static AvmRule RULE = new AvmRule(true);

    // default address with balance
    private static Address preminedAddress = RULE.getPreminedAccount();

    // contract address
    private static Address stakerRegistry;
    private static Address poolRegistry;

    @BeforeClass
    public static void deployDapp() {
        stakerRegistry = RULE.getRandomAddress(BigInteger.ZERO);

        byte[] arguments = ABIUtil.encodeDeploymentArguments(stakerRegistry );
        byte[] data = RULE.getDappBytes(PoolRegistry.class, arguments, PoolState.class);
        poolRegistry = RULE.deploy(preminedAddress, BigInteger.ZERO, data).getDappAddress();
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
        byte[] data = RULE.getDappBytes(PoolCoinbasee.class, arguments);
        System.out.println(Hex.toHexString(data));
        System.out.println(data.length);
    }

    @Test
    public void testRegister() {
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("registerPool")
                .encodeOneByteArray("test".getBytes())
                .encodeOneInteger(5)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);

        assertTrue(result.getReceiptStatus().isSuccess());
        assertTrue(result.getDecodedReturnData() instanceof Address);
    }
}

