package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import org.aion.avm.userlib.AionBuffer;

import java.math.BigInteger;

public class StakerRegistryStorage {

    // used for deriving storage key
    private enum StorageSlots {
        STAKE_INFO, // staker identityAddress -> total stake, self bond stake
        ADDRESS_INFO, // staker identityAddress -> staker signingAddress, coinbaseAddress
        SIGNING_ADDRESS, // staker signingAddress -> staker identityAddress
        MANAGEMENT_ADDRESS, // staker identityAddress -> staker managementAddress
        PENDING_UNDELEGATE, // undelegateId -> recipient, value, block number
        PENDING_TRANSFER, // transferId -> initiator, toStaker, recipient, value, block number
        STATE, // staker identityAddress -> state
    }

    /**
     * Puts the identity address associated with the signing address into storage
     *
     * @param signingAddress  staker signing address
     * @param identityAddress staker identity address. null will remove the address from storage.
     */
    protected static void putIdentityAddress(Address signingAddress, Address identityAddress) {
        byte[] key = getKey(StorageSlots.SIGNING_ADDRESS, signingAddress.toByteArray());
        byte[] value = identityAddress == null ? null : identityAddress.toByteArray();
        Blockchain.putStorage(key, value);
    }

    /**
     * Retrieves identity address corresponding to a signing address from storage
     *
     * @param signingAddress staker signing address
     * @return if signing address is present in storage, staker identity address. null otherwise
     */
    protected static Address getIdentityAddress(Address signingAddress) {
        byte[] key = getKey(StorageSlots.SIGNING_ADDRESS, signingAddress.toByteArray());
        byte[] value = Blockchain.getStorage(key);
        return value == null ? null : new Address(value);
    }

    /**
     * Puts the management address associated with the identity address into storage
     *
     * @param identityAddress   staker identity address
     * @param managementAddress staker management address, not null
     */
    protected static void putManagementAddress(Address identityAddress, Address managementAddress) {
        byte[] key = getKey(StorageSlots.MANAGEMENT_ADDRESS, identityAddress.toByteArray());
        Blockchain.putStorage(key, managementAddress.toByteArray());
    }

    /**
     * Retrieves management address corresponding to the identity address from storage
     *
     * @param identityAddress staker identity address
     * @return if identity address is present in storage, staker management address. null otherwise
     */
    protected static Address getManagementAddress(Address identityAddress) {
        byte[] key = getKey(StorageSlots.MANAGEMENT_ADDRESS, identityAddress.toByteArray());
        byte[] value = Blockchain.getStorage(key);
        return value == null ? null : new Address(value);
    }

    /**
     * Puts the signing address, coinbase address, and last block where signing address was updated into storage
     *
     * @param identityAddress staker identity address
     * @param addressInfo     staker address info, not null
     */
    protected static void putStakerAddressInfo(Address identityAddress, StakerStorageObjects.AddressInfo addressInfo) {
        byte[] key = getKey(StorageSlots.ADDRESS_INFO, identityAddress.toByteArray());
        byte[] value = addressInfo.serialize();
        Blockchain.putStorage(key, value);
    }

    /**
     * Retrieves the signing address, coinbase address, and last block where signing address was updated from storage
     *
     * @param identityAddress staker identity address
     * @return if identityAddress address is present in storage, address info, null otherwise
     */
    protected static StakerStorageObjects.AddressInfo getStakerAddressInfo(Address identityAddress) {
        byte[] key = getKey(StorageSlots.ADDRESS_INFO, identityAddress.toByteArray());
        byte[] value = Blockchain.getStorage(key);
        return value == null ? null : StakerStorageObjects.AddressInfo.from(value);
    }

    /**
     * Puts the total stake and self bond stake into storage
     *
     * @param identityAddress staker identity address
     * @param stakeInfo       staker stake info, not null
     */
    protected static void putStakerStakeInfo(Address identityAddress, StakerStorageObjects.StakeInfo stakeInfo) {
        byte[] key = getKey(StorageSlots.STAKE_INFO, identityAddress.toByteArray());
        byte[] value = stakeInfo.serialize();
        Blockchain.putStorage(key, value);
    }

    /**
     * Retrieves the stake info (total stake and self bond stake) of the staker from storage
     *
     * @param identityAddress identity address of the staker
     * @return if identityAddress address is present in storage, stake info, null otherwise
     */
    protected static StakerStorageObjects.StakeInfo getStakerStakeInfo(Address identityAddress) {
        byte[] key = getKey(StorageSlots.STAKE_INFO, identityAddress.toByteArray());
        byte[] value = Blockchain.getStorage(key);
        return value == null ? null : StakerStorageObjects.StakeInfo.from(value);
    }

    /**
     * Puts new pending undelegate info into storage
     *
     * @param undelegateId      undelegate identifier
     * @param pendingUndelegate undelegate info
     */
    protected static void putPendingUndelegte(long undelegateId, StakerStorageObjects.PendingUndelegate pendingUndelegate) {
        byte[] key = getKey(StorageSlots.PENDING_UNDELEGATE, BigInteger.valueOf(undelegateId).toByteArray());
        byte[] value = (pendingUndelegate == null) ? null : pendingUndelegate.serialize();
        Blockchain.putStorage(key, value);
    }

    /**
     * Retrieves pending undelegate info from storage
     *
     * @param undelegateId undelegate identifier
     * @return PendingUndelegate if transferId present, null otherwise
     */
    protected static StakerStorageObjects.PendingUndelegate getPendingUndelegate(long undelegateId) {
        byte[] key = getKey(StorageSlots.PENDING_UNDELEGATE, BigInteger.valueOf(undelegateId).toByteArray());
        byte[] value = Blockchain.getStorage(key);
        return value == null ? null : StakerStorageObjects.PendingUndelegate.from(value);
    }

    /**
     * Puts new pending transfer into storage
     *
     * @param transferId      transfer identifier
     * @param pendingTransfer transfer info
     */
    protected static void putPendingTransfer(long transferId, StakerStorageObjects.PendingTransfer pendingTransfer) {
        byte[] key = getKey(StorageSlots.PENDING_TRANSFER, BigInteger.valueOf(transferId).toByteArray());
        byte[] value = (pendingTransfer == null) ? null : pendingTransfer.serialize();
        Blockchain.putStorage(key, value);
    }

    /**
     * Retrieves pending transfer from storage
     *
     * @param transferId transfer identifier
     * @return PendingTransfer if transferId present, null otherwise
     */
    protected static StakerStorageObjects.PendingTransfer getPendingTransfer(long transferId) {
        byte[] key = getKey(StorageSlots.PENDING_TRANSFER, BigInteger.valueOf(transferId).toByteArray());
        byte[] value = Blockchain.getStorage(key);
        return value == null ? null : StakerStorageObjects.PendingTransfer.from(value);
    }

    /**
     * Puts staker state into storage
     *
     * @param identityAddress identity address of the staker
     * @param state           staker state
     */
    protected static void putState(Address identityAddress, boolean state) {
        byte[] key = getKey(StorageSlots.STATE, identityAddress.toByteArray());
        byte[] value = new byte[]{(byte) (state ? 1 : 0)};
        Blockchain.putStorage(key, value);
    }

    /**
     * Gets a staker state from storage
     *
     * @param identityAddress identity address of the staker
     * @return state true means an active state and false is broken state
     */
    protected static boolean getState(Address identityAddress){
        byte[] key = getKey(StorageSlots.STATE, identityAddress.toByteArray());
        byte[] value = Blockchain.getStorage(key);
        return value[0] != 0;
    }

    private static byte[] getKey(Enum storageSlot, byte[] key) {
        int outputSize = Integer.BYTES + key.length;
        AionBuffer buffer = AionBuffer.allocate(outputSize);
        buffer.putInt(storageSlot.hashCode());
        buffer.put(key);

        return Blockchain.blake2b(buffer.getArray());
    }
}
