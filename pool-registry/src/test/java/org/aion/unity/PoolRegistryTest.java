package org.aion.unity;

import avm.Address;

import org.aion.avm.core.util.ABIUtil;
import org.aion.avm.tooling.AvmRule;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.Arrays;

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

        byte[] arguments = ABIUtil.encodeDeploymentArguments(stakerRegistry);
        byte[] jar = RULE.getDappBytes(PoolRegistry.class, arguments);
        poolRegistry = RULE.deploy(preminedAddress, BigInteger.ZERO, jar).getDappAddress();
    }

    @Test
    public void testGetStakerRegistry() {
        byte[] txData = ABIUtil.encodeMethodArguments("getStakerRegistry");
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);

        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(stakerRegistry, result.getDecodedReturnData());
    }

    @Test
    public void testPoolContract() {
        byte[] arguments = ABIUtil.encodeDeploymentArguments(poolRegistry);
        byte[] jar = RULE.getDappBytes(PoolRegistry.class, arguments);
        System.out.println(Hex.toHexString(jar));
        System.out.println(Arrays.toString(jar));
    }
}

