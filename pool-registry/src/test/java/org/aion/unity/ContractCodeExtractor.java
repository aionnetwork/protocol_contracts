package org.aion.unity;

import avm.Address;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.embed.AvmRule;
import org.aion.avm.tooling.ABIUtil;
import org.junit.ClassRule;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

/**
 * The result of these method are used in PoolRegistry contract (as optimized DApp bytes)
 * Note that if tests need to be run in debug mode, these values should be updated accordingly.
 * This is due to the renaming optimization which is run only in normal execution mode.
 * This can cause problems in the avm if a test running in debug mode calls a contract which was deployed in normal execution mode.
 */
public class ContractCodeExtractor {

    private Address placeHolder = new Address(
            Helpers.hexStringToBytes("0000000000000000000000000000000000000000000000000000000000000000"));

    @ClassRule
    public static AvmRule avmRule = new AvmRule(false);

    @Test
    public void getPoolCoinbaseContractCode() {
        byte[] arguments = ABIUtil.encodeDeploymentArguments(placeHolder);
        byte[] data = avmRule.getDappBytes(PoolCoinbase.class, arguments);
        System.out.println(Hex.toHexString(data));
        System.out.println(data.length);
    }

    @Test
    public void getPoolCustodianContractCode() {
        byte[] arguments = ABIUtil.encodeDeploymentArguments(placeHolder, placeHolder);
        byte[] data = avmRule.getDappBytes(PoolCustodian.class, arguments);
        System.out.println(Hex.toHexString(data));
        System.out.println(data.length);
    }
}
