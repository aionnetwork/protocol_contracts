package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import org.aion.avm.userlib.AionBuffer;
import org.aion.avm.userlib.AionUtilities;

import java.math.BigInteger;

public class StakerRegistryEvents {

    static void registeredStaker(Address identityAddress, Address managementAddress,
                                 Address signingAddress, Address coinbaseAddress, Address selfBondAddress) {
        // todo check after address finalization
        byte[] data = new byte[Address.LENGTH * 2];
        System.arraycopy(managementAddress.toByteArray(), 0, data, 0, Address.LENGTH);
        System.arraycopy(selfBondAddress.toByteArray(), 0, data, Address.LENGTH, Address.LENGTH);

        Blockchain.log("StakerRegistered".getBytes(),
                identityAddress.toByteArray(),
                signingAddress.toByteArray(),
                coinbaseAddress.toByteArray(),
                data);
    }

    static void setSigningAddress(Address identityAddress, Address newAddress) {
        Blockchain.log("SigningAddressSet".getBytes(),
                identityAddress.toByteArray(),
                newAddress.toByteArray());
    }

    static void setCoinbaseAddress(Address identityAddress, Address newAddress) {
        Blockchain.log("CoinbaseAddressSet".getBytes(),
                identityAddress.toByteArray(),
                newAddress.toByteArray());
    }

    static void voted(Address delegator, Address staker, BigInteger value) {
        Blockchain.log("Voted".getBytes(),
                delegator.toByteArray(),
                staker.toByteArray(),
                value.toByteArray());
    }

    static void unvoted(long id, Address delegator, Address staker, Address recipient, BigInteger amount) {
        byte[] amountBytes = amount.toByteArray();
        byte[] data = new byte[Address.LENGTH + amountBytes.length];
        System.arraycopy(recipient.toByteArray(), 0, data, 0, Address.LENGTH);
        System.arraycopy(amountBytes, 0, data, Address.LENGTH, amountBytes.length);

        Blockchain.log("Unvoted".getBytes(),
                AionUtilities.padLeft(BigInteger.valueOf(id).toByteArray()),
                delegator.toByteArray(),
                staker.toByteArray(),
                data);
    }

    static void transferredStake(long id, Address fromStaker, Address toStaker, Address recipient, BigInteger amount) {
        byte[] data = AionBuffer.allocate(Address.LENGTH + 32) //64
                .putAddress(toStaker)
                .put32ByteInt(amount)
                .getArray();
        Blockchain.log("StakeTransferred".getBytes(),
                AionUtilities.padLeft(BigInteger.valueOf(id).toByteArray()),
                fromStaker.toByteArray(),
                recipient.toByteArray(),
                data);
    }

    static void finalizedUnvote(long id) {
        Blockchain.log("UnvoteFinalized".getBytes(),
                BigInteger.valueOf(id).toByteArray());
    }

    static void finalizedTransfer(long id) {
        Blockchain.log("TransferFinalized".getBytes(),
                BigInteger.valueOf(id).toByteArray());
    }

    static void setSelfBondAddress(Address identityAddress, Address newAddress) {
        Blockchain.log("SelfBondAddressSet".getBytes(),
                identityAddress.toByteArray(),
                newAddress.toByteArray());
    }
}
