package org.aion.unity;

import avm.Address;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.embed.AvmRule;
import org.aion.avm.tooling.ABIUtil;
import org.aion.avm.tooling.deploy.OptimizedJarBuilder;
import org.junit.ClassRule;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * This test includes methods that are used to build resources
 */
public class ContractCodeExtractor {

    private Address placeHolder = new Address(
            Helpers.hexStringToBytes("0000000000000000000000000000000000000000000000000000000000000000"));

    @ClassRule
    public static AvmRule avmRule = new AvmRule(false);

    /**
     * The result of this method is used in PoolRegistry contract (as optimized DApp bytes)
     * Note that if tests need to be run in debug mode, these values should be updated accordingly.
     * This is due to the renaming optimization which is run only in normal execution mode.
     * This can cause problems in the avm if a test running in debug mode calls a contract which was deployed in normal execution mode.
     */
    @Test
    public void getPoolCoinbaseContractCode() {
        byte[] arguments = ABIUtil.encodeDeploymentArguments(placeHolder);
        byte[] data = avmRule.getDappBytes(PoolCoinbase.class, arguments, 1);
        System.out.println(Hex.toHexString(data));
        System.out.println(data.length);
    }

    /**
     * Used to build the contract jar file
     */
    @Test
    public void buildJar() {
        Class[] otherClasses = {PoolStorageObjects.class, PoolRewardsStateMachine.class, PoolRegistryEvents.class, PoolRegistryStorage.class};
        byte[] jar = JarBuilder.buildJarForMainAndClasses(PoolRegistry.class, otherClasses);
        byte[] optimizedJar = (new OptimizedJarBuilder(false, jar, 1))
                .withUnreachableMethodRemover()
                .withRenamer()
                .withConstantRemover()
                .getOptimizedBytes();
        DataOutputStream dout = null;
        try {
            dout = new DataOutputStream(new FileOutputStream("poolRegistry.jar"));
            dout.write(optimizedJar);
            dout.close();
        } catch (IOException e) {
            System.err.println("Failed to create the jar.");
            e.printStackTrace();
        }
    }
}
