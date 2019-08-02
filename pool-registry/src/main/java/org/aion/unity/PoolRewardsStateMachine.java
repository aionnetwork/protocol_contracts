package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import org.aion.avm.userlib.AionMap;

import java.util.Map;

public class PoolRewardsStateMachine {
    // pool variables
    private final int fee; // 0-100%

    // state variables
    private long accumulatedStake; // stake accumulated in the pool
    private long accumulatedBlockRewards; // rewards paid to pool per block

    // commission is handled separately
    private long accumulatedCommission;
    private long withdrawnCommission;

    private long outstandingRewards; // total coins (as rewards), owned by the pool

    private long currentRewards; // rewards accumulated this period

    private Map<Address, Long> settledRewards = new AionMap<>(); // rewards in the "settled" state
    private Map<Address, Long> withdrawnRewards = new AionMap<>(); // rewards withdrawn from the pool, by each delegator

    private Map<Address, StartingInfo> delegations; // total delegations per delegator

    // TODO: can't use arbitrary precision real numbers here :(
    Decimal currentCRR;
    Decimal prevCRR;

    long getWithdrawnRewards(Address delegator) {
        return getOrDefault(withdrawnRewards, delegator, 0L);
    }

    // Initialize pool
    public PoolRewardsStateMachine(int fee) {
        assert (fee >= 0 && fee <= 100);
        this.fee = fee;

        currentCRR = Decimal.ZERO;
        prevCRR = Decimal.ZERO;

        delegations = new AionMap<>();
    }

    /* ----------------------------------------------------------------------
     * Leave and Join Functions
     * ----------------------------------------------------------------------*/

    /**
     * @return the bonded stake that just "left"
     */
    private long leave(Address delegator, long blockNumber) {
        assert (delegator != null && delegations.containsKey(delegator)); // sanity check

        incrementPeriod();
        long rewards = calculateUnsettledRewards(delegator, blockNumber);

        settledRewards.put(delegator, rewards + getOrDefault(settledRewards, delegator, 0L));

        StartingInfo startingInfo = delegations.get(delegator);
        long stake = startingInfo.stake;

        delegations.remove(delegator);

        accumulatedStake -= stake;

        return stake;
    }

    private void join(Address delegator, long blockNumber, long stake) {
        assert (delegator != null && !delegations.containsKey(delegator)); // sanity check

        // add this new delegation to our store
        delegations.put(delegator, new StartingInfo(stake, blockNumber, currentCRR));

        accumulatedStake += stake;
    }

    /* ----------------------------------------------------------------------
     * "Internal" Functions used by Leave and Join
     * ----------------------------------------------------------------------*/

    private void incrementPeriod() {
        // deal with the block rewards
        long commission = Decimal.valueOf(fee * accumulatedBlockRewards)
                .divideTruncate(Decimal.valueOf(100))
                .getTruncated().longValueExact();
        long shared = accumulatedBlockRewards - commission;

        this.accumulatedCommission += commission;
        this.currentRewards += shared;
        this.outstandingRewards += accumulatedBlockRewards;

        // "reset" the block rewards accumulator
        accumulatedBlockRewards = 0;

        // deal with the CRR computations
        if (accumulatedStake > 0) {
            prevCRR = currentCRR;

            Decimal crr = Decimal.valueOf(currentRewards).divideTruncate(Decimal.valueOf(accumulatedStake));
            currentCRR = currentCRR.add(crr);
        } else {
            // if there is no stake, then there should be no way to have accumulated rewards
            assert (currentRewards == 0);
        }

        currentRewards = 0;
    }

    private long calculateUnsettledRewards(Address delegator, long blockNumber) {
        StartingInfo startingInfo = delegations.get(delegator);

        if (startingInfo == null) {
            return 0;
        }

        // cannot calculate delegation rewards for blocks before stake was delegated
        assert (startingInfo.blockNumber <= blockNumber);

        // if a new period was created this block, then no rewards could be "settled" at this block
        if (startingInfo.blockNumber == blockNumber)
            return 0L;

        long stake = startingInfo.stake;

        // return stake * (ending - starting)
        Decimal startingCRR = startingInfo.crr;
        Decimal endingCRR = currentCRR;
        Decimal differenceCRR = endingCRR.subtract(startingCRR);

        Blockchain.println("CCR: start = " + startingCRR + ", end = " + endingCRR + ", diff = " + differenceCRR);

        return differenceCRR.multiplyTruncate(Decimal.valueOf(stake)).getTruncated().longValueExact();
    }

    /* ----------------------------------------------------------------------
     * Contract Lifecycle Functions
     * ----------------------------------------------------------------------*/
    public void onUnvote(Address delegator, long blockNumber, long stake) {
        assert (delegations.containsKey(delegator));
        long prevBond = delegations.get(delegator).stake;
        assert (stake <= prevBond); // make sure the amount of unvote requested is legal.

        long unbondedStake = leave(delegator, blockNumber);
        assert (unbondedStake == prevBond);

        // if they didn't fully un-bond, re-bond the remaining amount
        long nextBond = prevBond - stake;
        if (nextBond > 0) {
            join(delegator, blockNumber, nextBond);
        }
    }

    public void onVote(Address delegator, long blockNumber, long stake) {
        assert (stake >= 0);

        long prevBond = 0L;
        if (delegations.containsKey(delegator))
            prevBond = leave(delegator, blockNumber);
        else
            incrementPeriod();

        long nextBond = prevBond + stake;
        join(delegator, blockNumber, nextBond);
    }

    public long onRevote(Address delegator, long blockNumber) {
        long unbondedStake = 0;
        if (delegations.containsKey(delegator)) {
            // do a "leave-and-join"
            unbondedStake = leave(delegator, blockNumber);
        }

        long rewards = getOrDefault(settledRewards, delegator, 0L);
        settledRewards.remove(delegator);

        if (unbondedStake + rewards > 0) {
            join(delegator, blockNumber, unbondedStake + rewards);
        }

        return rewards;
    }

    /**
     * Withdraw is all or nothing, since that is both simpler, implementation-wise and does not make
     * much sense for people to partially withdraw. The problem we run into is that if the amount requested
     * for withdraw, can be less than the amount settled, in which case, it's not obvious if we should perform
     * a settlement ("leave") or save on gas and just withdraw out the rewards.
     */
    public long onWithdraw(Address delegator, long blockNumber) {
        if (delegations.containsKey(delegator)) {
            // do a "leave-and-join"
            long unbondedStake = leave(delegator, blockNumber);
            join(delegator, blockNumber, unbondedStake);
        }

        // if I don't see a delegation, then you must have been settled already.

        // now that all rewards owed to you are settled, you can withdraw them all at once
        long rewards = getOrDefault(settledRewards, delegator, 0L);
        settledRewards.remove(delegator);

        withdrawnRewards.put(delegator, rewards + getOrDefault(withdrawnRewards, delegator, 0L));
        outstandingRewards -= rewards;

        return rewards;
    }

    public long onWithdrawOperator() {
        long c = accumulatedCommission;
        accumulatedCommission = 0;

        withdrawnCommission += c;
        outstandingRewards -= c;

        return c;
    }

    public long getRewards(Address delegator, long blockNumber) {
        long unsettledRewards = calculateUnsettledRewards(delegator, blockNumber);
        long settledRewards = getOrDefault(this.settledRewards, delegator, 0L);

        return unsettledRewards + settledRewards;
    }

    public void onBlock(long blockNumber, long blockReward) {
        assert (blockNumber > 0 && blockReward > 0); // sanity check

        accumulatedBlockRewards += blockReward;
    }

    private static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultValue) {
        if (map.containsKey(key)) {
            return map.get(key);
        } else {
            return defaultValue;
        }
    }

    private static class StartingInfo {
        public long stake;             // amount of coins being delegated
        public long blockNumber;       // block number at which delegation was created
        public Decimal crr;

        public StartingInfo(long stake, long blockNumber, Decimal crr) {
            this.stake = stake;
            this.blockNumber = blockNumber;
            this.crr = crr;
        }
    }
}