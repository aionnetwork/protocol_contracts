package org.aion;

import org.aion.avm.core.util.ABIUtil;
import avm.Address;
import org.aion.avm.tooling.AvmRule;
import org.aion.vm.api.interfaces.ResultCode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.math.BigInteger;


public class StakingRegistryTest {
    @Rule
    public AvmRule RULE = new AvmRule(true);

    @Test
    public void testDeployAndCall() {
        // The contract doesn't yet do anything so just verify that we can deploy it and successfully call it.
        Address preminedAddress = RULE.getPreminedAccount();
        
        byte[] dAppBytes = RULE.getDappBytes(org.aion.StakingRegistry.class, null);
        Address dAppAddress = RULE.deploy(preminedAddress, BigInteger.ZERO, dAppBytes).getDappAddress();
        
        byte[] txData = new byte[0];
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, dAppAddress, BigInteger.ZERO, txData);
        
        ResultCode status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
    }
}
