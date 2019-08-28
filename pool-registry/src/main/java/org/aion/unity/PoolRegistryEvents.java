package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import org.aion.avm.userlib.AionBuffer;
import org.aion.avm.userlib.AionUtilities;

import java.math.BigInteger;

public class PoolRegistryEvents {

    // Note that pool's associated addresses are included in an StakerRegistry contract event
    static void registeredPool(Address identityAddress, int commissionRate, byte[] metaDataContentHash, byte[] metaDataUrl) {
        Blockchain.log("ADSPoolRegistered".getBytes(),
                identityAddress.toByteArray(),
                AionUtilities.padLeft(BigInteger.valueOf(commissionRate).toByteArray()),
                metaDataContentHash,
                metaDataUrl);
    }

    // Note that autoDelegateRewards and redelegate do not have dedicated event and emit the delegated event.
    static void delegated(Address delegator, Address pool, BigInteger value) {
        Blockchain.log("ADSDelegated".getBytes(),
                delegator.toByteArray(),
                pool.toByteArray(),
                value.toByteArray());
    }

    // identity
    static void undelegated(long id, Address delegator, Address pool, BigInteger amount) {
        Blockchain.log("ADSUndelegated".getBytes(),
                AionUtilities.padLeft(BigInteger.valueOf(id).toByteArray()),
                delegator.toByteArray(),
                pool.toByteArray(),
                amount.toByteArray());
    }

    static void transferredStake(long id, Address caller, Address fromPool, Address toPool, BigInteger amount) {
        byte[] data = AionBuffer.allocate(Address.LENGTH + 32) //64
                .putAddress(toPool)
                .put32ByteInt(amount)
                .getArray();
        Blockchain.log("ADSStakeTransferred".getBytes(),
                AionUtilities.padLeft(BigInteger.valueOf(id).toByteArray()),
                caller.toByteArray(),
                fromPool.toByteArray(),
                data);
    }

    static void withdrew(Address caller, Address pool, BigInteger amount) {
        Blockchain.log("ADSWithdrew".getBytes(),
                caller.toByteArray(),
                pool.toByteArray(),
                amount.toByteArray());
    }

    static void enabledAutoRewardsDelegation(Address caller, Address pool, int feePercentage) {
        Blockchain.log("ADSAutoRewardsDelegationEnabled".getBytes(),
                caller.toByteArray(),
                pool.toByteArray(),
                BigInteger.valueOf(feePercentage).toByteArray());
    }

    static void disabledAutoRewardsDelegation(Address caller, Address pool) {
        Blockchain.log("ADSAutoRewardsDelegationDisabled".getBytes(),
                caller.toByteArray(),
                pool.toByteArray());
    }

    static void updatedCommissionRate(Address pool, int newCommissionRate) {
        Blockchain.log("ADSCommissionRateUpdated".getBytes(),
                pool.toByteArray(),
                BigInteger.valueOf(newCommissionRate).toByteArray());
    }

    static void updatedMetaDataUrl(Address pool, byte[] newMetaDataUrl) {
        Blockchain.log("ADSMetaDataUrlUpdated".getBytes(),
                pool.toByteArray(),
                newMetaDataUrl);
    }

    static void updateMetaDataContentHash(Address pool, byte[] newMetaDataContentHash) {
        Blockchain.log("ADSMetaDataContentHashUpdated".getBytes(),
                pool.toByteArray(),
                newMetaDataContentHash);
    }

    // following events are implemented only in StakerRegistry
//    - finalizedUnvote
//    - finalizedTransfer
//    - setSigningAddress

}
