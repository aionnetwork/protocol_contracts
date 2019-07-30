package org.aion.unity;

import avm.Address;

import org.aion.avm.core.util.ABIUtil;
import org.aion.avm.tooling.AvmRule;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PoolRegistryTest {

    @ClassRule
    public static AvmRule avmRule = new AvmRule(true);

    // default address with balance
    private static Address from = avmRule.getPreminedAccount();

    // contract address
    private static Address stakerRegistry = new Address(new byte[32]);
    private static Address poolRegistry;

    @BeforeClass
    public static void deployDapp() {
        byte[] arguments = ABIUtil.encodeDeploymentArguments(stakerRegistry);
        byte[] dapp = avmRule.getDappBytes(PoolRegistry.class, arguments);
        poolRegistry = avmRule.deploy(from, BigInteger.ZERO, dapp).getDappAddress();
    }

    @Test
    public void testGetStakerRegistry() {
        byte[] txData = ABIUtil.encodeMethodArguments("getStakerRegistry");
        AvmRule.ResultWrapper result = avmRule.call(from, poolRegistry, BigInteger.ZERO, txData);

        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(stakerRegistry, result.getDecodedReturnData());
    }
}

