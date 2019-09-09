package org.aion.unity;

import avm.Address;
import org.aion.avm.userlib.AionBuffer;

import java.math.BigInteger;

public class PoolStorageObjects {
    static class DelegatorInfo {
        BigInteger stake;
        BigInteger settledRewards;
        BigInteger startingCrr;
        long startingCrrBlockNumber;

        protected DelegatorInfo() {
            stake = BigInteger.ZERO;
            settledRewards = BigInteger.ZERO;
            startingCrr = BigInteger.ZERO;
        }

        private DelegatorInfo(BigInteger stake, BigInteger settledRewards, BigInteger startingCrr, long startingCrrBlockNumber) {
            this.stake = stake;
            this.settledRewards = settledRewards;
            this.startingCrrBlockNumber = startingCrrBlockNumber;
            this.startingCrr = startingCrr;
        }

        protected byte[] serialize() {
            int length = 32 * 3 + Long.BYTES;
            AionBuffer aionBuffer = AionBuffer.allocate(length);
            aionBuffer.put32ByteInt(stake);
            aionBuffer.put32ByteInt(settledRewards);
            aionBuffer.put32ByteInt(startingCrr);
            aionBuffer.putLong(startingCrrBlockNumber);

            return aionBuffer.getArray();
        }

        protected static DelegatorInfo from(byte[] serializedBytes) {
            AionBuffer buffer = AionBuffer.wrap(serializedBytes);
            return new DelegatorInfo(buffer.get32ByteInt(), buffer.get32ByteInt(), buffer.get32ByteInt(), buffer.getLong());
        }
    }

    static class PoolRewards {
        BigInteger accumulatedStake = BigInteger.ZERO;
        BigInteger accumulatedCommission = BigInteger.ZERO;
        BigInteger outstandingRewards = BigInteger.ZERO;
        BigInteger currentCRR = BigInteger.ZERO;
        BigInteger accumulatedBlockRewards = BigInteger.ZERO;

        Address coinbaseAddress;

        // commission rate set by the pool owner
        int commissionRate;

        // fee used for calculating rewards and also applying punishment of not meeting the self bond percentage minimum
        int appliedCommissionRate;

        protected PoolRewards(Address coinbaseAddress, int commissionRate) {
            this.coinbaseAddress = coinbaseAddress;
            this.commissionRate = commissionRate;
        }

        private PoolRewards(BigInteger accumulatedStake, BigInteger accumulatedCommission, BigInteger outstandingRewards, BigInteger currentCRR,
                            BigInteger accumulatedBlockRewards, Address coinbaseAddress, int appliedCommissionRate, int commissionRate) {
            this.accumulatedStake = accumulatedStake;
            this.accumulatedCommission = accumulatedCommission;
            this.outstandingRewards = outstandingRewards;
            this.currentCRR = currentCRR;
            this.appliedCommissionRate = appliedCommissionRate;
            this.accumulatedBlockRewards = accumulatedBlockRewards;
            this.coinbaseAddress = coinbaseAddress;
            this.commissionRate = commissionRate;
        }

        protected byte[] serialize() {
            int length = 32 * 5 + Address.LENGTH + Integer.BYTES * 2;
            AionBuffer aionBuffer = AionBuffer.allocate(length);
            aionBuffer.put32ByteInt(accumulatedStake);
            aionBuffer.put32ByteInt(accumulatedCommission);
            aionBuffer.put32ByteInt(outstandingRewards);
            aionBuffer.put32ByteInt(currentCRR);
            aionBuffer.put32ByteInt(accumulatedBlockRewards);
            aionBuffer.putAddress(coinbaseAddress);
            aionBuffer.putInt(appliedCommissionRate);
            aionBuffer.putInt(commissionRate);

            return aionBuffer.getArray();
        }

        protected static PoolRewards from(byte[] serializedBytes) {
            AionBuffer buffer = AionBuffer.wrap(serializedBytes);
            return new PoolRewards(buffer.get32ByteInt(), buffer.get32ByteInt(), buffer.get32ByteInt(), buffer.get32ByteInt(),
                    buffer.get32ByteInt(), buffer.getAddress(), buffer.getInt(), buffer.getInt());
        }
    }

    static class StakeTransfer {
        Address initiator;
        Address fromPool;
        Address toPool;
        BigInteger amount;

        protected StakeTransfer(Address initiator, Address fromPool, Address toPool, BigInteger amount) {
            this.initiator = initiator;
            this.fromPool = fromPool;
            this.toPool = toPool;
            this.amount = amount;
        }

        protected byte[] serialize() {
            int length = Address.LENGTH * 3 + 32;
            AionBuffer aionBuffer = AionBuffer.allocate(length);
            aionBuffer.putAddress(initiator);
            aionBuffer.putAddress(fromPool);
            aionBuffer.putAddress(toPool);
            aionBuffer.put32ByteInt(amount);
            return aionBuffer.getArray();
        }

        protected static StakeTransfer from(byte[] serializedBytes) {
            AionBuffer buffer = AionBuffer.wrap(serializedBytes);
            return new StakeTransfer(buffer.getAddress(), buffer.getAddress(), buffer.getAddress(), buffer.get32ByteInt());
        }
    }

    static class CommissionUpdate {
        Address pool;
        int newCommissionRate;
        long blockNumber;

        protected CommissionUpdate(Address pool, int newCommissionRate, long blockNumber) {
            this.pool = pool;
            this.newCommissionRate = newCommissionRate;
            this.blockNumber = blockNumber;
        }

        protected byte[] serialize() {
            int length = Address.LENGTH + Integer.BYTES + Long.BYTES;
            AionBuffer aionBuffer = AionBuffer.allocate(length);
            aionBuffer.putAddress(pool);
            aionBuffer.putInt(newCommissionRate);
            aionBuffer.putLong(blockNumber);
            return aionBuffer.getArray();
        }

        protected static CommissionUpdate from(byte[] serializedBytes) {
            AionBuffer buffer = AionBuffer.wrap(serializedBytes);
            return new CommissionUpdate(buffer.getAddress(), buffer.getInt(), buffer.getLong());
        }
    }
}
