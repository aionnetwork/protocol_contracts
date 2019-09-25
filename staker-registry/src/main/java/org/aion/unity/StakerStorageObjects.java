package org.aion.unity;

import avm.Address;
import org.aion.avm.userlib.AionBuffer;

import java.math.BigInteger;

public class StakerStorageObjects {
    static class StakeInfo {
        // stake that is delegated. Delegation can only be done by the management address through delegate(), and it should be used by the PoolRegistry contract.
        BigInteger otherStake;
        // stake that is counted towards self bond. This is done by invoking bond()
        BigInteger selfBondStake;

        protected StakeInfo(BigInteger otherStake, BigInteger selfBondStake) {
            this.otherStake = otherStake;
            this.selfBondStake = selfBondStake;
        }

        protected byte[] serialize() {
            int length = 32 * 2;
            AionBuffer aionBuffer = AionBuffer.allocate(length);
            aionBuffer.put32ByteInt(otherStake);
            aionBuffer.put32ByteInt(selfBondStake);
            return aionBuffer.getArray();
        }

        protected static StakeInfo from(byte[] serializedBytes) {
            AionBuffer buffer = AionBuffer.wrap(serializedBytes);
            return new StakeInfo(buffer.get32ByteInt(), buffer.get32ByteInt());
        }
    }

    static class AddressInfo {
        Address signingAddress;
        Address coinbaseAddress;
        long lastSigningAddressUpdate;

        protected AddressInfo(Address signingAddress, Address coinbaseAddress, long lastSigningAddressUpdate) {
            this.signingAddress = signingAddress;
            this.coinbaseAddress = coinbaseAddress;
            this.lastSigningAddressUpdate = lastSigningAddressUpdate;
        }

        protected byte[] serialize() {
            int length = Address.LENGTH * 2 + Long.BYTES;
            AionBuffer aionBuffer = AionBuffer.allocate(length);
            aionBuffer.putAddress(signingAddress);
            aionBuffer.putAddress(coinbaseAddress);
            aionBuffer.putLong(lastSigningAddressUpdate);
            return aionBuffer.getArray();
        }

        protected static AddressInfo from(byte[] serializedBytes) {
            AionBuffer buffer = AionBuffer.wrap(serializedBytes);
            return new AddressInfo(buffer.getAddress(), buffer.getAddress(), buffer.getLong());
        }
    }

    static class PendingUndelegate {
        Address recipient;
        BigInteger value;
        BigInteger fee;
        long blockNumber;

        protected PendingUndelegate(Address recipient, BigInteger value, BigInteger fee, long blockNumber) {
            this.recipient = recipient;
            this.value = value;
            this.fee = fee;
            this.blockNumber = blockNumber;
        }

        protected byte[] serialize() {
            int length = Address.LENGTH + 32 * 2 + Long.BYTES;
            AionBuffer aionBuffer = AionBuffer.allocate(length);
            aionBuffer.putAddress(recipient);
            aionBuffer.put32ByteInt(value);
            aionBuffer.put32ByteInt(fee);
            aionBuffer.putLong(blockNumber);
            return aionBuffer.getArray();
        }

        protected static PendingUndelegate from(byte[] serializedBytes) {
            AionBuffer buffer = AionBuffer.wrap(serializedBytes);
            return new PendingUndelegate(buffer.getAddress(), buffer.get32ByteInt(), buffer.get32ByteInt(), buffer.getLong());
        }
    }

    static class PendingTransfer {
        Address initiator;
        Address toStaker;
        BigInteger value;
        BigInteger fee;
        long blockNumber;

        protected PendingTransfer(Address initiator, Address toStaker, BigInteger value, BigInteger fee, long blockNumber) {
            this.initiator = initiator;
            this.toStaker = toStaker;
            this.value = value;
            this.fee = fee;
            this.blockNumber = blockNumber;
        }

        protected byte[] serialize() {
            int length = Address.LENGTH * 3 + 32 * 2 + Long.BYTES;
            AionBuffer aionBuffer = AionBuffer.allocate(length);
            aionBuffer.putAddress(initiator);
            aionBuffer.putAddress(toStaker);
            aionBuffer.put32ByteInt(value);
            aionBuffer.put32ByteInt(fee);
            aionBuffer.putLong(blockNumber);
            return aionBuffer.getArray();
        }

        protected static PendingTransfer from(byte[] serializedBytes) {
            AionBuffer buffer = AionBuffer.wrap(serializedBytes);
            return new PendingTransfer(buffer.getAddress(), buffer.getAddress(), buffer.get32ByteInt(), buffer.get32ByteInt(), buffer.getLong());
        }
    }
}
