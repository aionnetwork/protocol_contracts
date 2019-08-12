package org.aion.unity;

import avm.Address;
import org.aion.avm.tooling.AvmRule;
import org.junit.Rule;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AionBlockHeaderTest {


    private static BigInteger ENOUGH_BALANCE_TO_TRANSACT = BigInteger.TEN.pow(18 + 5);

    @Rule
    public AvmRule RULE = new AvmRule(true);

    private Address preminedAddress;

    private Address stakerAddress;
    private Address signingAddress;
    private Address coinbaseAddress;

    private Address stakerRegistry;


    // header dump from Aion Java Kernel
    //
    //  hash=5536a78aede06ba574a1971b7178565c1ad5425f98ff64afb728a2a33a892f70  Length: 32
    //  version=1  Length:
    //  number=2
    //  parentHash=6068a128093216a3c0bd9b8cecea132731b8d4fca67de87020b42965fd32a581  parentHash: 32
    //  coinbase=bee628af072dde474c426e5062b2c5ad6888ad3221fe949a1962df918159ded2  coinBase: 32
    //  stateRoot=f2953aeb18a5bcb88220ecc0f2c5e222d932f09cc7a26f724276c67fb1c301a5  stateRoot: 32
    //  txTrieHash=2e5def03d03e5e6a1b2b22c8185263920b36e056d4e7b1a0d9318764ce0758f3  txTrieRoot: 32
    //  receiptsTrieHash=90685796aa5ee0da1a7d27b5d43b7d52e9f8f2199f5d579489431524170d9828  receiptTrieRoot: 32
    //  difficulty=03  difficulty: 1
    //  energyConsumed=4
    //  energyLimit=5
    //  extraData=64617461
    //  timestamp=6 (1970.01.01 01:00:06)
    //  nonce=7061727431
    //  solution=7061727432
    //
    private byte[] vector1 = Hex.decode("f8dd0102a06068a128093216a3c0bd9b8cecea132731b8d4fca67de87020b42965fd32a581a0bee628af072dde474c426e5062b2c5ad6888ad3221fe949a1962df918159ded2a0f2953aeb18a5bcb88220ecc0f2c5e222d932f09cc7a26f724276c67fb1c301a5a02e5def03d03e5e6a1b2b22c8185263920b36e056d4e7b1a0d9318764ce0758f3a090685796aa5ee0da1a7d27b5d43b7d52e9f8f2199f5d579489431524170d9828a00000000000000000000000000000000000000000000000000000000000000000038464617461040506857061727431857061727432");

    @Test
    public void testDecode() {
        AionBlockHeader header = new AionBlockHeader(vector1);
        System.out.println(header);

        assertEquals(1, header.version);
        assertEquals(2, header.number);
        assertEquals("6068a128093216a3c0bd9b8cecea132731b8d4fca67de87020b42965fd32a581", Hex.toHexString(header.parentHash));
        assertEquals("bee628af072dde474c426e5062b2c5ad6888ad3221fe949a1962df918159ded2", Hex.toHexString(header.coinbase));
        assertEquals("f2953aeb18a5bcb88220ecc0f2c5e222d932f09cc7a26f724276c67fb1c301a5", Hex.toHexString(header.stateRoot));
        assertEquals("2e5def03d03e5e6a1b2b22c8185263920b36e056d4e7b1a0d9318764ce0758f3", Hex.toHexString(header.txTrieRoot));
        assertEquals("90685796aa5ee0da1a7d27b5d43b7d52e9f8f2199f5d579489431524170d9828", Hex.toHexString(header.receiptTrieRoot));
        assertEquals(3, new BigInteger(1, header.difficulty).intValue());
        assertEquals(4, header.energyConsumed);
        assertEquals(5, header.energyLimit);
        assertEquals("64617461", Hex.toHexString(header.extraData));
        assertEquals(6, header.timestamp);
        assertEquals("7061727431", Hex.toHexString(header.nonce));
        assertEquals("7061727432", Hex.toHexString(header.solution));
    }


    @Test
    public void testDeploy() {
        // setup accounts
        preminedAddress = RULE.getPreminedAccount();

        stakerAddress = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);
        signingAddress = RULE.getRandomAddress(BigInteger.ZERO);
        coinbaseAddress = RULE.getRandomAddress(BigInteger.ZERO);

        // deploy the staker registry contract
        byte[] jar = RULE.getDappBytes(StakerRegistry.class, null, AionBlockHeader.class, RlpDecoder.class, RlpEncoder.class, RlpList.class, RlpString.class, RlpType.class);
        AvmRule.ResultWrapper result = RULE.deploy(preminedAddress, BigInteger.ZERO, jar);
        assertTrue(result.getReceiptStatus().isSuccess());
    }
}
