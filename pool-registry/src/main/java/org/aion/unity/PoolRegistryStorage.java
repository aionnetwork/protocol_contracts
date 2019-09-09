package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import org.aion.avm.userlib.AionBuffer;

import java.math.BigInteger;

public class PoolRegistryStorage {

    private enum StorageSlots {
        DELEGATION, // pool address, delegator address -> stake, settledRewards, crr, starting period block number
        AUTO_REWARDS_DELEGATION, //pool address, auto delegator -> fee
        POOL_META_DATA, // pool address -> metadata content hash, metadata url
        POOL_REWARDS, // pool address -> coinbase address, applied fee, fee, crr, outstanding rewards, total stake, accumulated block rewards
        PENDING_COMMISSION_RATE, // commission rate change id -> pool address, new commission rate, block number
        PENDING_TRANSFER, // transfer id -> initiator, from pool, to pool, amount
        COINBASE_CONTRACT // -> coinbase contract bytes
    }

    /**
     * Puts delegator info into storage
     *
     * @param pool          pool address
     * @param delegator     delegator address
     * @param delegatorInfo information to store. null value will remove the delegator info
     */
    protected static void putDelegator(Address pool, Address delegator, PoolStorageObjects.DelegatorInfo delegatorInfo) {
        byte[] key = getKey(StorageSlots.DELEGATION, concatAddresses(pool.toByteArray(), delegator.toByteArray()));
        byte[] value = delegatorInfo == null ? null : delegatorInfo.serialize();
        Blockchain.putStorage(key, value);
    }

    /**
     * Retrieves the delegator info from storage
     *
     * @param pool      pool address
     * @param delegator delegator address
     * @return if delegator address is present in storage, DelegatorInfo initialized to the values in the storage,
     * otherwise a new DelegatorInfo with values set to zero
     */
    protected static PoolStorageObjects.DelegatorInfo getDelegator(Address pool, Address delegator) {
        byte[] key = getKey(StorageSlots.DELEGATION, concatAddresses(pool.toByteArray(), delegator.toByteArray()));
        byte[] value = Blockchain.getStorage(key);
        return value == null ? new PoolStorageObjects.DelegatorInfo() : PoolStorageObjects.DelegatorInfo.from(value);
    }

    /**
     * Puts pool's reward info into storage
     *
     * @param pool        pool address
     * @param poolRewards information to store, not null
     */
    protected static void putPoolRewards(Address pool, PoolStorageObjects.PoolRewards poolRewards) {
        byte[] key = getKey(StorageSlots.POOL_REWARDS, pool.toByteArray());
        byte[] value = poolRewards.serialize();
        Blockchain.putStorage(key, value);
    }

    /**
     * Retrieves pool's rewards information from storage
     *
     * @param pool pool address
     * @return if pool address is present in storage, PoolRewards initialized to the values in the storage,
     * null otherwise
     */
    protected static PoolStorageObjects.PoolRewards getPoolRewards(Address pool) {
        byte[] key = getKey(StorageSlots.POOL_REWARDS, pool.toByteArray());
        byte[] value = Blockchain.getStorage(key);
        return value == null ? null : PoolStorageObjects.PoolRewards.from(value);
    }

    /**
     * Puts pool metadata into storage
     *
     * @param pool                pool address
     * @param metaDataContentHash meta-data content hash, not null
     * @param metaDataUrl         meta-data url, not null
     */
    protected static void putPoolMetaData(Address pool, byte[] metaDataContentHash, byte[] metaDataUrl) {
        byte[] key = getKey(StorageSlots.POOL_META_DATA, pool.toByteArray());
        byte[] value = new byte[metaDataContentHash.length + metaDataUrl.length];
        System.arraycopy(metaDataContentHash, 0, value, 0, metaDataContentHash.length);
        System.arraycopy(metaDataUrl, 0, value, metaDataContentHash.length, metaDataUrl.length);
        Blockchain.putStorage(key, value);
    }

    /**
     * Retrieves pool's meta-data from storage
     *
     * @param pool pool address
     * @return concatenated metadata. First 32 bytes represent metaDataContentHash, and the rest is metaDataUrl
     */
    protected static byte[] getPoolMetaData(Address pool) {
        byte[] key = getKey(StorageSlots.POOL_META_DATA, pool.toByteArray());
        return Blockchain.getStorage(key);
    }

    /**
     * Puts new pending transfer into storage
     *
     * @param transferId transfer identifier
     * @param transfer   transfer info
     */
    protected static void putPendingTransfer(long transferId, PoolStorageObjects.StakeTransfer transfer) {
        byte[] key = getKey(StorageSlots.PENDING_TRANSFER, BigInteger.valueOf(transferId).toByteArray());
        byte[] value = (transfer == null) ? null : transfer.serialize();
        Blockchain.putStorage(key, value);
    }

    /**
     * Retrieves pending transfer from storage
     *
     * @param transferId transfer identifier
     * @return StakeTransfer if transferId present, null otherwise
     */
    protected static PoolStorageObjects.StakeTransfer getPendingTransfer(long transferId) {
        byte[] key = getKey(StorageSlots.PENDING_TRANSFER, BigInteger.valueOf(transferId).toByteArray());
        byte[] value = Blockchain.getStorage(key);
        return value == null ? null : PoolStorageObjects.StakeTransfer.from(value);
    }

    /**
     * Puts pending commission rate request into storage
     *
     * @param commissionUpdateId commission change request identifier
     * @param commissionUpdate   information to store
     */
    protected static void putPendingCommissionUpdate(long commissionUpdateId, PoolStorageObjects.CommissionUpdate commissionUpdate) {
        byte[] key = getKey(StorageSlots.PENDING_COMMISSION_RATE, BigInteger.valueOf(commissionUpdateId).toByteArray());
        byte[] value = (commissionUpdate == null) ? null : commissionUpdate.serialize();
        Blockchain.putStorage(key, value);
    }

    /**
     * Retrieves pending commission rate request from storage
     *
     * @param commissionUpdateId commission change request identifier
     * @return CommissionUpdate if identifier present, null otherwise
     */
    protected static PoolStorageObjects.CommissionUpdate getPendingCommissionUpdate(long commissionUpdateId) {
        byte[] key = getKey(StorageSlots.PENDING_COMMISSION_RATE, BigInteger.valueOf(commissionUpdateId).toByteArray());
        byte[] value = Blockchain.getStorage(key);
        return value == null ? null : PoolStorageObjects.CommissionUpdate.from(value);
    }

    /**
     * Puts auto reward delegation info in storage
     *
     * @param pool      pool address
     * @param delegator delegator address
     * @param fee       the auto-delegation fee. if set to -1, it will remove the auto delegation value from storage
     */
    protected static void putAutoDelegationFee(Address pool, Address delegator, int fee) {
        byte[] key = getKey(StorageSlots.AUTO_REWARDS_DELEGATION, concatAddresses( pool.toByteArray(), delegator.toByteArray()));
        byte[] value = (fee == -1) ? null : BigInteger.valueOf(fee).toByteArray();
        Blockchain.putStorage(key, value);
    }

    /**
     * Retrieves auto reward delegation info
     *
     * @param pool      pool address
     * @param delegator delegator address
     * @return the auto-delegation fee if delegator has setup auto-delegation. -1 otherwise
     */
    protected static int getAutoDelegationFee(Address pool, Address delegator) {
        byte[] key = getKey(StorageSlots.AUTO_REWARDS_DELEGATION, concatAddresses( pool.toByteArray(), delegator.toByteArray()));
        byte[] value = Blockchain.getStorage(key);
        return value == null ? -1 : new BigInteger(value).intValueExact();
    }

    /**
     * Puts coinbase contract bytes into storage
     *
     * @param coinbaseContract coinbase contract
     */
    protected static void putCoinbaseContractBytes(byte[] coinbaseContract) {
        byte[] key = getKey(StorageSlots.COINBASE_CONTRACT, new byte[0]);
        Blockchain.putStorage(key, coinbaseContract);
    }

    /**
     * Retrieves coinbase contract from storage
     *
     * @return coinbase contract
     */
    protected static byte[] getCoinbaseContractBytes() {
        byte[] key = getKey(StorageSlots.COINBASE_CONTRACT, new byte[0]);
        return Blockchain.getStorage(key);
    }

    private static byte[] concatAddresses(byte[] address1, byte[] address2) {
        byte[] result = new byte[Address.LENGTH * 2];
        System.arraycopy(address1, 0, result, 0, Address.LENGTH);
        System.arraycopy(address2, 0, result, Address.LENGTH, Address.LENGTH);
        return result;
    }

    private static byte[] getKey(Enum storageSlot, byte[] key) {
        int outputSize = Integer.BYTES + key.length;
        AionBuffer buffer = AionBuffer.allocate(outputSize);
        buffer.putInt(storageSlot.hashCode());
        buffer.put(key);

        return Blockchain.blake2b(buffer.getArray());
    }
}
