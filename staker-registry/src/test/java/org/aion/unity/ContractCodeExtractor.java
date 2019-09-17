package org.aion.unity;

import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.embed.AvmRule;
import org.aion.avm.tooling.deploy.OptimizedJarBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * This test includes methods that are used to build resources
 */
public class ContractCodeExtractor {

    private Class[] otherClasses = {StakerRegistryEvents.class, StakerStorageObjects.class, StakerRegistryStorage.class};

    @Rule
    public AvmRule RULE = new AvmRule(false);

    /**
     * Used to get the jar bytes of the StakerRegistry contract
     * The result of this method is used in PoolRegistryTest.
     */
    @Test
    public void testPrintJarInHex() {
        byte[] jar = RULE.getDappBytes(StakerRegistry.class, null, 1, otherClasses);
        System.out.println(Hex.toHexString(jar));
    }

    /**
     * Used to build the contract jar file
     */
    @Test
    public void buildJar() {
        byte[] jar = JarBuilder.buildJarForMainAndClasses(StakerRegistry.class, otherClasses);
        byte[] optimizedJar = (new OptimizedJarBuilder(false, jar, 1))
                .withUnreachableMethodRemover()
                .withRenamer()
                .withConstantRemover()
                .getOptimizedBytes();

        DataOutputStream dout = null;
        try {
            dout = new DataOutputStream(new FileOutputStream("stakerRegistry.jar"));
            dout.write(optimizedJar);
            dout.close();
        } catch (IOException e) {
            System.err.println("Failed to create the jar.");
            e.printStackTrace();
        }

    }
}
