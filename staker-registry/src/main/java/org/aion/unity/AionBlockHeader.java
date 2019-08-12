package org.aion.unity;

import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

import java.util.List;

public class AionBlockHeader {

    byte version;

    long number;

    byte[] parentHash;

    byte[] coinbase;

    byte[] stateRoot;

    byte[] txTrieRoot;

    byte[] receiptTrieRoot;

    byte[] logsBloom;

    byte[] difficulty;

    byte[] extraData;

    long energyConsumed;

    long energyLimit;

    long timestamp;

    byte[] nonce;

    byte[] solution;

    public AionBlockHeader(byte[] rlpEncoded) {
        List<RlpType> list = RlpDecoder.decode(rlpEncoded).getValues();
        List<RlpType> header = ((RlpList) list.get(0)).getValues();

        this.version = ((RlpString) (header.get(0))).asPositiveBigInteger().byteValueExact();
        this.number = ((RlpString) (header.get(1))).asPositiveBigInteger().byteValueExact();
        this.parentHash = ((RlpString) (header.get(2))).getBytes();
        this.coinbase = ((RlpString) (header.get(3))).getBytes();
        this.stateRoot = ((RlpString) (header.get(4))).getBytes();
        this.txTrieRoot = ((RlpString) (header.get(5))).getBytes();
        this.receiptTrieRoot = ((RlpString) (header.get(6))).getBytes();
        this.logsBloom = ((RlpString) (header.get(7))).getBytes();
        this.difficulty = ((RlpString) (header.get(8))).getBytes();
        this.extraData = ((RlpString) (header.get(9))).getBytes();
        this.energyConsumed = ((RlpString) (header.get(10))).asPositiveBigInteger().longValue();
        this.energyLimit = ((RlpString) (header.get(11))).asPositiveBigInteger().longValue();
        this.timestamp = ((RlpString) (header.get(12))).asPositiveBigInteger().longValue();
        this.nonce = ((RlpString) (header.get(13))).getBytes();
        this.solution = ((RlpString) (header.get(14))).getBytes();
    }

    @Override
    public String toString() {
        return "AionBlockHeader{" +
                "version=" + version +
                ", number=" + number +
                ", parentHash=" + bytesToHex(parentHash) +
                ", coinbase=" + bytesToHex(coinbase) +
                ", stateRoot=" + bytesToHex(stateRoot) +
                ", txTrieRoot=" + bytesToHex(txTrieRoot) +
                ", receiptTrieRoot=" + bytesToHex(receiptTrieRoot) +
                ", logsBloom=" + bytesToHex(logsBloom) +
                ", difficulty=" + bytesToHex(difficulty) +
                ", extraData=" + bytesToHex(extraData) +
                ", energyConsumed=" + energyConsumed +
                ", energyLimit=" + energyLimit +
                ", timestamp=" + timestamp +
                ", nonce=" + bytesToHex(nonce) +
                ", solution=" + bytesToHex(solution) +
                '}';
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}
