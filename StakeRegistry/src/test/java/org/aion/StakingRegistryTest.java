package org.aion;

import avm.Address;
import org.aion.avm.tooling.AvmRule;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;
import org.aion.vm.api.interfaces.ResultCode;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.math.BigInteger;


public class StakingRegistryTest {
    @Rule
    public AvmRule RULE = new AvmRule(true);

    /**
     * This test verifies what the bootstrap.sh script does:
     * 1) Install staking contract
     * 2) Register as staker
     * 3) Vote for ourselves
     * 4) Verify that the vote was registered
     */
    @Test
    public void testBootstrap() throws Exception {
        Address preminedAddress = RULE.getPreminedAccount();
        // We will vote with 1 billion.
        BigInteger valueToVote = new BigInteger("1000000000");
        
        byte[] dAppBytes = RULE.getDappBytes(org.aion.StakingRegistry.class, null);
        Address dAppAddress = RULE.deploy(preminedAddress, BigInteger.ZERO, dAppBytes).getDappAddress();
        
        // Register.
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("register")
                .encodeOneAddress(preminedAddress)
                .toBytes()
                ;
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, dAppAddress, BigInteger.ZERO, txData);
        ResultCode status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
        
        // Vote.
        txData = new ABIStreamingEncoder()
                .encodeOneString("vote")
                .encodeOneAddress(preminedAddress)
                .toBytes()
                ;
        result = RULE.call(preminedAddress, dAppAddress, valueToVote, txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
        
        // Verify.
        txData = new ABIStreamingEncoder()
                .encodeOneString("getVote")
                .encodeOneAddress(preminedAddress)
                .toBytes()
                ;
        result = RULE.call(preminedAddress, dAppAddress, BigInteger.ZERO, txData);
        status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
        long valueFound = new ABIDecoder(result.getTransactionResult().getReturnData()).decodeOneLong();
        Assert.assertEquals(1_000_000_000L, valueFound);
    }
}
