package org.aion.unity.distribution.f1;

import avm.Address;
import org.aion.unity.distribution.RewardsManager;

import java.util.*;

public class F1RewardsManager extends RewardsManager {

    @SuppressWarnings({"WeakerAccess"})
    public class StartingInfo {
        public double stake;             // amount of coins being delegated
        public long blockNumber;       // block number at which delegation was created
        public double crr;

        public StartingInfo(double stake, long blockNumber, double crr) {
            this.stake = stake;
            this.blockNumber = blockNumber;
            this.crr = crr;
        }
    }

    private class PoolStateMachine {
        // pool variables
        private final double fee;

        // state variables
        private long accumulatedStake; // stake accumulated in the pool

        // commission is handled separately
        private double accumulatedCommission;
        private double withdrawnCommission;

        private double outstandingRewards; // total coins (as rewards) owned by the pool

        private double currentRewards; // rewards accumulated this period

        private Map<Address, Double> settledRewards = new HashMap<>(); // rewards in the "settled" state
        private Map<Address, Double> withdrawnRewards = new HashMap<>(); // rewards withdrawn from the pool, by each delegator

        private Map<Address, StartingInfo> delegations; // total delegations per delegator

        double currentCRR;
        double prevCRR;

        double getWithdrawnRewards(Address delegator) {
            return withdrawnRewards.getOrDefault(delegator, 0d);
        }

        // Initialize pool
        PoolStateMachine(double fee) {
            this.fee = fee;

            currentCRR = 0d;
            prevCRR = 0d;

            delegations = new HashMap<>();
        }

        /* ----------------------------------------------------------------------
         * Leave and Join Functions
         * ----------------------------------------------------------------------*/

        /**
         * @return the bonded stake that just "left"
         */
        private double leave(Address delegator, long blockNumber) {
            assert (delegator != null && delegations.containsKey(delegator)); // sanity check

            incrementPeriod();
            double rewards = calculateUnsettledRewards(delegator, blockNumber);

            settledRewards.put(delegator, rewards + settledRewards.getOrDefault(delegator, 0d));

            StartingInfo startingInfo = delegations.get(delegator);
            double stake = startingInfo.stake;

            delegations.remove(delegator);

            accumulatedStake -= stake;

            return stake;
        }

        private void join(Address delegator, long blockNumber, double stake) {
            assert (delegator != null && !delegations.containsKey(delegator)); // sanity check

            // add this new delegation to our store
            delegations.put(delegator, new StartingInfo(stake, blockNumber, currentCRR));

            accumulatedStake += stake;
        }

        /* ----------------------------------------------------------------------
         * "Internal" Functions used by Leave and Join
         * ----------------------------------------------------------------------*/

        private void incrementPeriod() {
            if (accumulatedStake > 0) {
                prevCRR = currentCRR;
                currentCRR += currentRewards / accumulatedStake;
            }

            currentRewards = 0;
        }

        private double calculateUnsettledRewards(Address delegator, long blockNumber) {
            StartingInfo startingInfo = delegations.get(delegator);

            // cannot calculate delegation rewards for blocks before stake was delegated
            assert (startingInfo.blockNumber <= blockNumber);

            if (startingInfo.blockNumber == blockNumber)
                return 0D;

            double stake = startingInfo.stake;

            // return stake * (ending - starting)
            double startingCRR = startingInfo.crr;
            double endingCRR = currentCRR;
            double differenceCRR = endingCRR - startingCRR;

            if (differenceCRR < 0) {
                throw new RuntimeException("Negative rewards should not be possible");
            }

            return (differenceCRR * stake);
        }

        /* ----------------------------------------------------------------------
         * Contract Lifecycle Functions
         * ----------------------------------------------------------------------*/
        public void onUnvote(Address delegator, long blockNumber, double stake) {
            assert (delegations.containsKey(delegator));
            double prevBond = delegations.get(delegator).stake;
            assert (stake <= prevBond); // make sure the amount of unvote requested is legal.

            double unbondedStake = leave(delegator, blockNumber);
            assert (unbondedStake == prevBond);

            // if they didn't fully un-bond, re-bond the remaining amount
            double nextBond = prevBond - stake;
            if (nextBond > 0) {
                join(delegator, blockNumber, nextBond);
            }
        }

        public void onVote(Address delegator, long blockNumber, double stake) {
            assert (stake >= 0);

            double prevBond = 0d;
            if (delegations.containsKey(delegator))
                prevBond = leave(delegator, blockNumber);
            else
                incrementPeriod();

            double nextBond = prevBond + stake;
            join(delegator, blockNumber, nextBond);
        }

        public void onWithdraw(Address delegator, long blockNumber, long limit) {
            if (delegations.containsKey(delegator)) {
                // do a "leave-and-join"
                double unbondedStake = leave(delegator, blockNumber);
                join(delegator, blockNumber, unbondedStake);
            }

            // if I don't see a delegation, then you must have been settled already.

            // now that all rewards owed to you are settled, you can withdraw them all at once
            double settledRewards = this.settledRewards.getOrDefault(delegator, 0d);
            double withdraw = Math.min(settledRewards, limit);
            this.settledRewards.put(delegator, settledRewards - withdraw);

            withdrawnRewards.put(delegator, withdrawnRewards.getOrDefault(delegator, 0d) + withdraw);
            outstandingRewards -= withdraw;
        }

        public double onWithdrawOperator() {
            double c = accumulatedCommission;
            accumulatedCommission = 0;

            withdrawnCommission += c;
            outstandingRewards -= c;

            return c;
        }

        /**
         * On block production, we need to withhold the pool commission, and update the rewards managed by this pool.
         * ref: https://github.com/cosmos/cosmos-sdk/blob/master/x/distribution/keeper/allocation.go
         */
        public void onBlock(long blockNumber, double blockReward) {
            assert (blockNumber > 0 && blockReward > 0); // sanity check

            double commission = fee * blockReward;
            double shared = blockReward - commission;

            this.accumulatedCommission += commission;
            this.currentRewards += shared;
            this.outstandingRewards += blockReward;
        }
    }

    @Override
    public Map<Address, Long> computeRewards(List<Event> events) {
        PoolStateMachine sm = new PoolStateMachine(0D);
        Set<Address> addresses = new HashSet<>();

        assert (events.size() > 0);

        // to remember the last block seen, for owed calculation at the end
        long blockNumber = events.get(0).blockNumber;
        for (Event event : events) {
            Address delegator = event.source;
            blockNumber = event.blockNumber;
            long amount = event.amount;

            if (event.type != EventType.BLOCK)
                addresses.add(delegator);

            switch (event.type) {
                case VOTE: {
                    sm.onVote(delegator, blockNumber, amount);
                    break;
                }
                case UNVOTE: {
                    sm.onUnvote(delegator, blockNumber, amount);
                    break;
                }
                case WITHDRAW: {
                    sm.onWithdraw(delegator, blockNumber, amount);
                    break;
                }
                case BLOCK: {
                    sm.onBlock(blockNumber, amount);
                    break;
                }
            }
        }

        // finalize the owed + withdrawn rewards
        Map<Address, Long> rewards = new HashMap<>();
        for (Address a : addresses) {
            sm.onWithdraw(a, blockNumber, Long.MAX_VALUE);
            rewards.put(a, (long) sm.getWithdrawnRewards(a));
        }

        return rewards;
    }
}