package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import org.aion.avm.userlib.AionUtilities;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;

import java.math.BigInteger;

public class StakerRegistryEvents {

    protected static void registeredStaker(Address identityAddress, Address managementAddress,
                                 Address signingAddress, Address coinbaseAddress) {

        Blockchain.log("StakerRegistered".getBytes(),
                identityAddress.toByteArray(),
                signingAddress.toByteArray(),
                coinbaseAddress.toByteArray(),
                managementAddress.toByteArray());
    }

    protected static void setSigningAddress(Address identityAddress, Address newAddress) {
        Blockchain.log("SigningAddressSet".getBytes(),
                identityAddress.toByteArray(),
                newAddress.toByteArray());
    }

    protected static void setCoinbaseAddress(Address identityAddress, Address newAddress) {
        Blockchain.log("CoinbaseAddressSet".getBytes(),
                identityAddress.toByteArray(),
                newAddress.toByteArray());
    }

    protected static void delegated(Address staker, BigInteger value) {
        Blockchain.log("delegated".getBytes(),
                staker.toByteArray(),
                value.toByteArray());
    }

    protected static void undelegated(long id, Address staker, Address recipient, BigInteger amount, BigInteger fee) {
        byte[] data = new byte[(32 + 2) * 2];
        new ABIStreamingEncoder(data).encodeOneBigInteger(amount).encodeOneBigInteger(fee);

        Blockchain.log("Undelegated".getBytes(),
                AionUtilities.padLeft(BigInteger.valueOf(id).toByteArray()),
                staker.toByteArray(),
                recipient.toByteArray(),
                data);
    }

    protected static void transferredDelegation(long id, Address fromStaker, Address toStaker, BigInteger amount, BigInteger fee) {
        byte[] data = new byte[(32 + 2) * 2];
        new ABIStreamingEncoder(data).encodeOneBigInteger(amount).encodeOneBigInteger(fee);

        Blockchain.log("DelegationTransferred".getBytes(),
                AionUtilities.padLeft(BigInteger.valueOf(id).toByteArray()),
                fromStaker.toByteArray(),
                toStaker.toByteArray(),
                data);
    }

    protected static void finalizedUndelegation(long id) {
        Blockchain.log("UndelegationFinalized".getBytes(),
                BigInteger.valueOf(id).toByteArray());
    }

    protected static void finalizedDelegationTransfer(long id) {
        Blockchain.log("TransferFinalized".getBytes(),
                BigInteger.valueOf(id).toByteArray());
    }

    // Events for self bond stake are different to allow the distinction between normal delegation and self-bond stake
    protected static void bonded(Address identityAddress, BigInteger amount) {
        Blockchain.log("Bonded".getBytes(),
                identityAddress.toByteArray(),
                amount.toByteArray());
    }

    protected static void unbonded(long id, Address staker, Address recipient, BigInteger amountBI) {
        Blockchain.log("Unbonded".getBytes(),
                AionUtilities.padLeft(BigInteger.valueOf(id).toByteArray()),
                staker.toByteArray(),
                recipient.toByteArray(),
                amountBI.toByteArray());
    }

    protected static void changedState(Address staker, boolean state){
        Blockchain.log("StateChanged".getBytes(),
                staker.toByteArray(),
                new byte[]{(byte) (state ? 1 : 0)});
    }
}
