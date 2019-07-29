package org.aion.unity;

import avm.Address;

import org.aion.avm.core.util.ABIUtil;
import org.aion.avm.tooling.AvmRule;
import org.aion.unity.model.Pool;
import org.aion.unity.model.Staker;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PoolRegistryImplTest {

    @ClassRule
    public static AvmRule avmRule = new AvmRule(true);

    // default address with balance
    private static Address from = avmRule.getPreminedAccount();

    // contract address
    private static Address stakeRegistry = new Address(new byte[32]);
    private static Address delegationRegistry;

    @BeforeClass
    public static void deployDapp() {
        byte[] arguments = ABIUtil.encodeDeploymentArguments(stakeRegistry);
        byte[] dapp = avmRule.getDappBytes(PoolRegistryImpl.class, arguments, PoolRegistry.class, Pool.class, Staker.class);
        delegationRegistry = avmRule.deploy(from, BigInteger.ZERO, dapp).getDappAddress();
    }

    @Test
    public void testGetName() {
        byte[] txData = ABIUtil.encodeMethodArguments("getNames");
        AvmRule.ResultWrapper result = avmRule.call(from, delegationRegistry, BigInteger.ZERO, txData);

        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals("Stake Delegation Registry", result.getDecodedReturnData());
    }

    @Test
    public void testGetStakeRegistry() {
        byte[] txData = ABIUtil.encodeMethodArguments("getStakeRegistry");
        AvmRule.ResultWrapper result = avmRule.call(from, delegationRegistry, BigInteger.ZERO, txData);

        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(stakeRegistry, result.getDecodedReturnData());
    }
}

