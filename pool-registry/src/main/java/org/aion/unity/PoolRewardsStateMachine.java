package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import org.aion.avm.userlib.AionMap;

import java.math.BigInteger;
import java.util.Map;

/**
 * See https://github.com/ali-sharif/f1-fee-distribution for a PoC implementation of the F1 algorithm
 * TODO: need more tests for this class
 */
public class PoolRewardsStateMachine {
    // pool variables
    private BigInteger fee; // 0-100%

    // state variables
    private BigInteger accumulatedStake = BigInteger.ZERO; // stake accumulated in the pool
    private BigInteger accumulatedBlockRewards = BigInteger.ZERO; // rewards paid to pool per block

    // commission is handled separately
    private BigInteger accumulatedCommission = BigInteger.ZERO;
    private BigInteger withdrawnCommission = BigInteger.ZERO;

    private BigInteger outstandingRewards = BigInteger.ZERO; // total coins (as rewards), owned by the pool

    private BigInteger currentRewards = BigInteger.ZERO; // rewards accumulated this period

    private Map<Address, BigInteger> settledRewards = new AionMap<>(); // rewards in the "settled" state
    private Map<Address, BigInteger> withdrawnRewards = new AionMap<>(); // rewards withdrawn from the pool, by each delegator

    private Map<Address, StartingInfo> delegations; // total delegations per delegator

    BigInteger currentCRR;
    BigInteger prevCRR;

    // todo possible to replace multiplication and division with shift operation
    private static BigInteger precisionInt = new BigInteger("1000000000000000000000000000");
    private static BigInteger feeDivisor = new BigInteger("1000000");


    BigInteger getWithdrawnRewards(Address delegator) {
        return getOrDefault(withdrawnRewards, delegator, BigInteger.ZERO);
    }

    // Initialize pool
    public PoolRewardsStateMachine() {
        this.fee = BigInteger.ZERO;

        currentCRR = BigInteger.ZERO;
        prevCRR = BigInteger.ZERO;

        delegations = new AionMap<>();
    }

    /* ----------------------------------------------------------------------
     * Leave and Join Functions
     * ----------------------------------------------------------------------*/

    /**
     * @return the bonded stake that just "left"
     */
    private BigInteger leave(Address delegator, long blockNumber) {
        assert (delegator != null && delegations.containsKey(delegator)); // sanity check

        incrementPeriod();
        BigInteger rewards = calculateUnsettledRewards(delegator, blockNumber);

        settledRewards.put(delegator, rewards.add(getOrDefault(settledRewards, delegator, BigInteger.ZERO)));

        StartingInfo startingInfo = delegations.get(delegator);
        BigInteger stake = startingInfo.stake;

        delegations.remove(delegator);

        accumulatedStake = accumulatedStake.subtract(stake);

        return stake;
    }

    private void join(Address delegator, long blockNumber, BigInteger stake) {
        assert (delegator != null && !delegations.containsKey(delegator)); // sanity check

        // add this new delegation to our store
        delegations.put(delegator, new StartingInfo(stake, blockNumber, currentCRR));

        accumulatedStake = accumulatedStake.add(stake);
    }

    /* ----------------------------------------------------------------------
     * "Internal" Functions used by Leave and Join
     * ----------------------------------------------------------------------*/

    private void incrementPeriod() {
        // Blockchain.println("Increment period: acc_rewards = " + accumulatedBlockRewards);

        // deal with the block rewards
        BigInteger commission = (fee.multiply(accumulatedBlockRewards))
                .divide(feeDivisor);

        BigInteger shared = accumulatedBlockRewards.subtract(commission);

        this.accumulatedCommission = accumulatedCommission.add(commission);
        this.currentRewards = this.currentRewards.add(shared);
        this.outstandingRewards = this.outstandingRewards.add(accumulatedBlockRewards);

        // "reset" the block rewards accumulator
        accumulatedBlockRewards = BigInteger.ZERO;

        // deal with the CRR computations
        // accumulatedStake > 0
        if (accumulatedStake.signum() == 1) {
            prevCRR = currentCRR;

            // currentRewards (in nAmps) is multiplied by 10^27 to keep precision.
            // This is truncated during the calculation of the unsettled rewards for delegator
            BigInteger crr = currentRewards.multiply(precisionInt).divide(accumulatedStake);
            currentCRR = currentCRR.add(crr);
        } else {
            // if there is no stake, then there should be no way to have accumulated rewards
            assert (currentRewards.equals(BigInteger.ZERO));
        }

        currentRewards = BigInteger.ZERO;
    }

    private BigInteger calculateUnsettledRewards(Address delegator, long blockNumber) {
        StartingInfo startingInfo = delegations.get(delegator);

        if (startingInfo == null) {
            return BigInteger.ZERO;
        }

        // cannot calculate delegation rewards for blocks before stake was delegated
        assert (startingInfo.blockNumber <= blockNumber);

        // if a new period was created this block, then no rewards could be "settled" at this block
        if (startingInfo.blockNumber == blockNumber)
            return BigInteger.ZERO;

        BigInteger stake = startingInfo.stake;

        // return stake * (ending - starting)
        BigInteger startingCRR = startingInfo.crr;
        BigInteger endingCRR = currentCRR;
        BigInteger differenceCRR = endingCRR.subtract(startingCRR);

        Blockchain.println("CCR: start = " + startingCRR + ", end = " + endingCRR + ", diff = " + differenceCRR);

        // truncate the precision value
        return differenceCRR.multiply(stake).divide(precisionInt);
    }

    /* ----------------------------------------------------------------------
     * Contract Lifecycle Functions
     * ----------------------------------------------------------------------*/
    public void onUndelegate(Address delegator, long blockNumber, BigInteger stake) {
        assert (delegations.containsKey(delegator));
        BigInteger prevBond = delegations.get(delegator).stake;
        assert (stake.compareTo(prevBond) <= 0); // make sure the amount of undelegate requested is legal.

        BigInteger unbondedStake = leave(delegator, blockNumber);
        assert (unbondedStake.equals(prevBond));

        // if they didn't fully un-bond, re-bond the remaining amount
        BigInteger nextBond = prevBond.subtract(stake);
        // nextBond > 0
        if (nextBond.signum() == 1) {
            join(delegator, blockNumber, nextBond);
        }
    }

    public void onDelegate(Address delegator, long blockNumber, BigInteger stake) {
        assert (stake.signum() >= 0);

        BigInteger prevBond = BigInteger.ZERO;
        if (delegations.containsKey(delegator))
            prevBond = leave(delegator, blockNumber);
        else
            incrementPeriod();

        BigInteger nextBond = prevBond.add(stake);
        join(delegator, blockNumber, nextBond);
    }

    /**
     * Withdraw is all or nothing, since that is both simpler, implementation-wise and does not make
     * much sense for people to partially withdraw. The problem we run into is that if the amount requested
     * for withdraw, can be less than the amount settled, in which case, it's not obvious if we should perform
     * a settlement ("leave") or save on gas and just withdraw out the rewards.
     */
    public BigInteger onWithdraw(Address delegator, long blockNumber) {
        if (delegations.containsKey(delegator)) {
            // do a "leave-and-join"
            BigInteger unbondedStake = leave(delegator, blockNumber);
            join(delegator, blockNumber, unbondedStake);
        }

        // if I don't see a delegation, then you must have been settled already.

        // now that all rewards owed to you are settled, you can withdraw them all at once
        BigInteger rewards = getOrDefault(settledRewards, delegator, BigInteger.ZERO);
        settledRewards.remove(delegator);

        withdrawnRewards.put(delegator, rewards.add(getOrDefault(withdrawnRewards, delegator, BigInteger.ZERO)));
        outstandingRewards = outstandingRewards.subtract(rewards);

        return rewards;
    }

    public BigInteger onWithdrawOperator() {
        BigInteger c = accumulatedCommission;
        accumulatedCommission = BigInteger.ZERO;

        withdrawnCommission = withdrawnCommission.add(c);
        outstandingRewards = outstandingRewards.subtract(c);

        return c;
    }

    public void onBlock(long blockNumber, BigInteger blockReward) {
        // blockReward > 0
        assert (blockNumber > 0 && blockReward.signum() == 1); // sanity check

        accumulatedBlockRewards =  accumulatedBlockRewards.add(blockReward);
    }

    public BigInteger getRewards(Address delegator, long blockNumber) {
        BigInteger unsettledRewards = calculateUnsettledRewards(delegator, blockNumber);
        BigInteger settledRewards = getOrDefault(this.settledRewards, delegator, BigInteger.ZERO);

        return unsettledRewards.add(settledRewards);
    }

    public void setCommissionRate(int newRate) {
        incrementPeriod();
        fee = BigInteger.valueOf(newRate);
    }

    private static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultValue) {
        if (map.containsKey(key)) {
            return map.get(key);
        } else {
            return defaultValue;
        }
    }

    public boolean isFeeSetToZero() {
        return fee.signum() == 0;
    }

    private static class StartingInfo {
        public BigInteger stake;             // amount of coins being delegated
        public long blockNumber;       // block number at which delegation was created
        public BigInteger crr;

        public StartingInfo(BigInteger stake, long blockNumber, BigInteger crr) {
            this.stake = stake;
            this.blockNumber = blockNumber;
            this.crr = crr;
        }
    }
}
