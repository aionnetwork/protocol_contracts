package org.aion.unity;

import org.aion.avm.embed.AvmRule;
import org.junit.Rule;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

public class ContractCodeExtractor {

    private Class[] otherClasses = {StakerRegistryEvents.class};

    @Rule
    public AvmRule RULE = new AvmRule(false);

    /**
     * The result of this method is used in PoolRegistryTest.
     */
    @Test
    public void testPrintJarInHex() {
        byte[] jar = RULE.getDappBytes(StakerRegistry.class, null, 1,  otherClasses);
        System.out.println(Hex.toHexString(jar));
    }
}
