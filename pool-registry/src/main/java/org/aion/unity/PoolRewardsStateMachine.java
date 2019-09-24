package org.aion.unity;

import avm.Blockchain;

import java.math.BigInteger;

/**
 * See https://github.com/ali-sharif/f1-fee-distribution for a PoC implementation of the F1 algorithm
 * TODO: need more tests for this class
 */
public class PoolRewardsStateMachine {

    // todo possible to replace multiplication and division with shift operation
    private static BigInteger precisionInt = new BigInteger("1000000000000000000000000000");
    private static BigInteger feeDivisor = new BigInteger("1000000");

    PoolStorageObjects.PoolRewards currentPoolRewards;

    // Initialize pool
    public PoolRewardsStateMachine(PoolStorageObjects.PoolRewards poolRewards){
        currentPoolRewards = poolRewards;
    }

    /* ----------------------------------------------------------------------
     * Leave and Join Functions
     * ----------------------------------------------------------------------*/

    /**
     * @return the bonded stake that just "left"
     */
    private BigInteger leave(PoolStorageObjects.DelegatorInfo delegatorInfo, long blockNumber) {
        incrementPeriod();
        BigInteger rewards = calculateUnsettledRewards(delegatorInfo, blockNumber);

        delegatorInfo.settledRewards = rewards.add(delegatorInfo.settledRewards);

        BigInteger stake = delegatorInfo.stake;
        // reset delegator Info
        delegatorInfo.stake = BigInteger.ZERO;
        delegatorInfo.startingCrrBlockNumber = 0;
        delegatorInfo.startingCrr = BigInteger.ZERO;

        // sanity check
        assert stake.compareTo(currentPoolRewards.accumulatedStake) <= 0;

        currentPoolRewards.accumulatedStake = currentPoolRewards.accumulatedStake.subtract(stake);

        return stake;
    }

    private void join(PoolStorageObjects.DelegatorInfo delegatorInfo, long blockNumber, BigInteger stake) {
        // update delegator info with this new delegation
        // stake is the total stake of the delegator
        delegatorInfo.stake = stake;
        delegatorInfo.startingCrrBlockNumber = blockNumber;
        delegatorInfo.startingCrr = currentPoolRewards.currentCRR;
        currentPoolRewards.accumulatedStake = currentPoolRewards.accumulatedStake.add(stake);
    }

    /* ----------------------------------------------------------------------
     * "Internal" Functions used by Leave and Join
     * ----------------------------------------------------------------------*/

    private void incrementPeriod() {
        // Blockchain.println("Increment period: acc_rewards = " + accumulatedBlockRewards);

        // deal with the block rewards
        BigInteger commission = (BigInteger.valueOf(currentPoolRewards.commissionRate).multiply(currentPoolRewards.accumulatedBlockRewards))
                .divide(feeDivisor);

        BigInteger currentRewards = currentPoolRewards.accumulatedBlockRewards.subtract(commission);

        currentPoolRewards.accumulatedCommission = currentPoolRewards.accumulatedCommission.add(commission);
        currentPoolRewards.outstandingRewards = currentPoolRewards.outstandingRewards.add(currentPoolRewards.accumulatedBlockRewards);

        // "reset" the block rewards accumulator
        currentPoolRewards.accumulatedBlockRewards = BigInteger.ZERO;

        // deal with the CRR computations
        // accumulatedStake > 0
        if (currentPoolRewards.accumulatedStake.signum() == 1) {
            // currentRewards (in nAmps) is multiplied by 10^27 to keep precision.
            // This is truncated during the calculation of the unsettled rewards for delegator
            BigInteger crr = currentRewards.multiply(precisionInt).divide(currentPoolRewards.accumulatedStake);
            currentPoolRewards.currentCRR = currentPoolRewards.currentCRR.add(crr);
        } else {
            // if there is no stake, then there should be no way to have accumulated rewards
            assert (currentRewards.equals(BigInteger.ZERO));
        }
    }

    private BigInteger calculateUnsettledRewards(PoolStorageObjects.DelegatorInfo delegatorInfo, long blockNumber) {
        //if a delegator has not delegated yet or it has un-delegated the full stake amount, the stake will be zero and unsettledRewards will be zero as well.

        // cannot calculate delegation rewards for blocks before stake was delegated
        assert (delegatorInfo.startingCrrBlockNumber <= blockNumber);

        // if a new period was created this block, then no rewards could be "settled" at this block
        if (delegatorInfo.startingCrrBlockNumber == blockNumber) {
            return BigInteger.ZERO;
        }

        // return stake * (ending - starting)
        BigInteger startingCRR = delegatorInfo.startingCrr;
        BigInteger endingCRR = currentPoolRewards.currentCRR;
        BigInteger differenceCRR = endingCRR.subtract(startingCRR);

        Blockchain.println("CCR: start = " + startingCRR + ", end = " + endingCRR + ", diff = " + differenceCRR);

        // truncate the precision value
        return differenceCRR.multiply(delegatorInfo.stake).divide(precisionInt);
    }

    /* ----------------------------------------------------------------------
     * Contract Lifecycle Functions
     * ----------------------------------------------------------------------*/
    public void onUndelegate(PoolStorageObjects.DelegatorInfo delegatorInfo, long blockNumber, BigInteger stake) {
        BigInteger prevBond = delegatorInfo.stake;
        assert (stake.compareTo(prevBond) <= 0); // make sure the amount of undelegate requested is legal.

        BigInteger unbondedStake = leave(delegatorInfo, blockNumber);
        assert (unbondedStake.equals(prevBond));

        // if they didn't fully un-bond, re-bond the remaining amount
        BigInteger nextBond = prevBond.subtract(stake);
        // nextBond > 0
        if (nextBond.signum() == 1) {
            join(delegatorInfo, blockNumber, nextBond);
        }
    }

    public void onDelegate(PoolStorageObjects.DelegatorInfo delegatorInfo, long blockNumber, BigInteger stake) {
        BigInteger prevBond = BigInteger.ZERO;
        if (!delegatorInfo.stake.equals(BigInteger.ZERO)) {
            prevBond = leave(delegatorInfo, blockNumber);
        } else {
            incrementPeriod();
        }

        BigInteger nextBond = prevBond.add(stake);
        join(delegatorInfo, blockNumber, nextBond);
    }

    /**
     * Withdraw is all or nothing, since that is both simpler, implementation-wise and does not make
     * much sense for people to partially withdraw. The problem we run into is that if the amount requested
     * for withdraw, can be less than the amount settled, in which case, it's not obvious if we should perform
     * a settlement ("leave") or save on gas and just withdraw out the rewards.
     */
    public BigInteger onWithdraw(PoolStorageObjects.DelegatorInfo delegatorInfo, long blockNumber) {
        if (!delegatorInfo.stake.equals(BigInteger.ZERO)) {
            // do a "leave-and-join"
            BigInteger unbondedStake = leave(delegatorInfo, blockNumber);
            join(delegatorInfo, blockNumber, unbondedStake);
        }

        // if I don't see a delegation, then you must have been settled already.

        // now that all rewards owed to you are settled, you can withdraw them all at once
        BigInteger rewards = delegatorInfo.settledRewards;
        delegatorInfo.settledRewards = BigInteger.ZERO;
        currentPoolRewards.outstandingRewards = currentPoolRewards.outstandingRewards.subtract(rewards);

        return rewards;
    }

    public BigInteger onWithdrawOperator() {
        BigInteger c = currentPoolRewards.accumulatedCommission;
        currentPoolRewards.accumulatedCommission = BigInteger.ZERO;

        currentPoolRewards.outstandingRewards = currentPoolRewards.outstandingRewards.subtract(c);

        return c;
    }

    public void onBlock(long blockNumber, BigInteger blockReward) {
        // blockReward > 0
        assert (blockNumber > 0 && blockReward.signum() == 1); // sanity check

        currentPoolRewards.accumulatedBlockRewards = currentPoolRewards.accumulatedBlockRewards.add(blockReward);
    }

    public void setCommissionRate(int newRate) {
        incrementPeriod();
        currentPoolRewards.commissionRate = newRate;
    }
}
