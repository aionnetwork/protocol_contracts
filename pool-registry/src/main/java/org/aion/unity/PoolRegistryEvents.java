package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import org.aion.avm.userlib.AionUtilities;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;

import java.math.BigInteger;

public class PoolRegistryEvents {

    // Note that pool's associated addresses are included in an StakerRegistry contract event
    protected static void registeredPool(Address identityAddress, int commissionRate, byte[] metaDataContentHash, byte[] metaDataUrl) {
        Blockchain.log("ADSPoolRegistered".getBytes(),
                identityAddress.toByteArray(),
                AionUtilities.padLeft(BigInteger.valueOf(commissionRate).toByteArray()),
                metaDataContentHash,
                metaDataUrl);
    }

    // Note that autoDelegateRewards and redelegate do not have dedicated event and emit the delegated event.
    protected static void delegated(Address delegator, Address pool, BigInteger value) {
        Blockchain.log("ADSDelegated".getBytes(),
                delegator.toByteArray(),
                pool.toByteArray(),
                value.toByteArray());
    }

    protected static void undelegated(long id, Address delegator, Address pool, BigInteger amount, BigInteger fee) {
        // 32 bytes for the  BigInteger value and 2 bytes for the encoding tokens
        byte[] data = new byte[(32 + 2) * 2];
        new ABIStreamingEncoder(data).encodeOneBigInteger(amount).encodeOneBigInteger(fee);
        Blockchain.log("ADSUndelegated".getBytes(),
                AionUtilities.padLeft(BigInteger.valueOf(id).toByteArray()),
                delegator.toByteArray(),
                pool.toByteArray(),
                data);
    }

    protected static void transferredDelegation(long id, Address caller, Address fromPool, Address toPool, BigInteger amount, BigInteger fee) {
        byte[] data = new byte[Address.LENGTH + 1 + (32 + 2) * 2];
        new ABIStreamingEncoder(data).encodeOneAddress(toPool).encodeOneBigInteger(amount).encodeOneBigInteger(fee);
        Blockchain.log("ADSDelegationTransferred".getBytes(),
                AionUtilities.padLeft(BigInteger.valueOf(id).toByteArray()),
                caller.toByteArray(),
                fromPool.toByteArray(),
                data);
    }

    protected static void withdrew(Address caller, Address pool, BigInteger amount) {
        Blockchain.log("ADSWithdrew".getBytes(),
                caller.toByteArray(),
                pool.toByteArray(),
                amount.toByteArray());
    }

    protected static void enabledAutoRewardsDelegation(Address caller, Address pool, int feePercentage) {
        Blockchain.log("ADSAutoRewardsDelegationEnabled".getBytes(),
                caller.toByteArray(),
                pool.toByteArray(),
                BigInteger.valueOf(feePercentage).toByteArray());
    }

    protected static void disabledAutoRewardsDelegation(Address caller, Address pool) {
        Blockchain.log("ADSAutoRewardsDelegationDisabled".getBytes(),
                caller.toByteArray(),
                pool.toByteArray());
    }

    protected static void requestedCommissionRateChange(long id, Address pool, int newCommissionRate) {
        Blockchain.log("ADSCommissionRateChangeRequested".getBytes(),
                AionUtilities.padLeft(BigInteger.valueOf(id).toByteArray()),
                pool.toByteArray(),
                BigInteger.valueOf(newCommissionRate).toByteArray());
    }

    protected static void updatedMetaData(Address pool, byte[] newMetaDataUrl, byte[] newMetaDataContentHash) {
        Blockchain.log("ADSPoolMetaDataUpdated".getBytes(),
                pool.toByteArray(),
                newMetaDataContentHash,
                newMetaDataUrl);
    }

    protected static void finalizedCommissionRateChange(long id) {
        Blockchain.log("ADSCommissionRateChangeFinalized".getBytes(),
                BigInteger.valueOf(id).toByteArray());
    }

    protected static void poolRegistryDeployed(Address stakerRegistry, BigInteger minSelfStake, BigInteger minSelfStakePercentage, long commissionRateChangeTimeLock) {
        Blockchain.log("ADSDeployed".getBytes(),
                stakerRegistry.toByteArray(),
                AionUtilities.padLeft(minSelfStake.toByteArray()),
                AionUtilities.padLeft(minSelfStakePercentage.toByteArray()),
                BigInteger.valueOf(commissionRateChangeTimeLock).toByteArray());
    }

    // following events are implemented only in StakerRegistry
//    - finalizedUnbond
//    - finalizedTransfer
//    - setSigningAddress
//    - changedState

}
