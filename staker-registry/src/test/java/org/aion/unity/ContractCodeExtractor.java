package org.aion.unity;

import org.aion.avm.embed.AvmRule;
import org.junit.Rule;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;
import org.web3j.rlp.*;

public class ContractCodeExtractor {
    private Class[] otherClasses = {
            AionBlockHeader.class, RlpDecoder.class, RlpEncoder.class, RlpList.class, RlpString.class, RlpType.class, Arrays.class, ByteArrayWrapper.class,
            StakerRegistryEvents.class
    };

    @Rule
    public AvmRule RULE = new AvmRule(false);

    /**
     * The result of this method is used in PoolRegistryTest.
     */
    @Test
    public void testPrintJarInHex() {
        byte[] jar = RULE.getDappBytes(StakerRegistry.class, null, otherClasses);
        System.out.println(Hex.toHexString(jar));
    }
}
