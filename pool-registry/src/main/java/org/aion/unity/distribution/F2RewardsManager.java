package org.aion.unity.distribution;

import avm.Address;
import org.aion.unity.distribution.util.Decimal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Notes on Precision Selection
 * ----------------------------
 * <p>
 * WLOG, all amounts are denominated in some units of "coin".
 * <p>
 * When we refer to "precision", we mean the number of precision places required to represent the smallest possible real
 * number that can arise when we divide the smallest unit of staked coin, with the largest size of the pool.
 * <p>
 * Such quantity required in the computation of the F1 rewards distribution scheme.
 * <p>
 * Consider the following degenerate case: all coins in the system are delegated to one pool, and a delegator has
 * staked 1 coin to this pool. In this case, the maximum precision "precision" we need is the number of precision places
 * required to represent (max # of coins in the system)^-1
 * <p>
 * Now, if there is an upper limit to the number of coins that can be delegated to a pool, then the "precision" we
 * need would be the number of precision places required to represent (max # of coins per pool)^-1. In the
 * absence of such a maximum, in the design of this system, we fall-back to the "precision" that depends on the max #
 * of coins in the system.
 * <p>
 * Furthermore, we run into another problem here; the max # of coins in the system has not been defined for Aion. In
 * order to resolve this, we consider the maximum number which can be represented by a 64-bit signed (Java) long,
 * the number of precision places required is ceil(log_10(2^63 - 1)) = ceil(18.96) = 19 ~ 20.
 * <p>
 * Assuming the AVM data-word limit of 128-bit unsigned integer, the number of precision places required
 * is ceil(log_10(2^128 - 1)) = ceil(38.53) = 39 ~ 40.
 * <p>
 * We pick 20 precision places as the "precision" in this proof of concept.
 * <p>
 * Under such a precision regime:
 * > All additions and subtractions can be computed without precision loss
 * > Multiplications would need to be performed with double the precision (40 precision places), which is then
 * truncated down to 20 precision places.
 * <p>
 * Miscellaneous Notes for the Implementor
 * ---------------------------------------
 * <p>
 * 1. We've used long to represent all "coin" units in this system. A decision needs to be made about the units used
 * in the smart contract; if we're using base units (nAmp) or Aion, or some other quanta of coin.
 */
@SuppressWarnings("unused")
public class F2RewardsManager extends RewardsManager {

    @SuppressWarnings({"WeakerAccess"})
    public class StartingInfo {
        public long stake;             // amount of coins being delegated
        public long blockNumber;       // block number at which delegation was created
        public Decimal crr;

        public StartingInfo(long stake, long blockNumber, Decimal crr) {
            this.stake = stake;
            this.blockNumber = blockNumber;
            this.crr = crr;
        }
    }

    @SuppressWarnings("WeakerAccess")
    private class PoolStateMachine {
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

        private Map<Address, Long> settledRewards = new HashMap<>(); // rewards in the "settled" state
        private Map<Address, Long> withdrawnRewards = new HashMap<>(); // rewards withdrawn from the pool, by each delegator

        private Map<Address, StartingInfo> delegations; // total delegations per delegator

        // TODO: can't use arbitrary precision real numbers here :(
        Decimal currentCRR;
        Decimal prevCRR;

        long getWithdrawnRewards(Address delegator) {
            return withdrawnRewards.getOrDefault(delegator, 0L);
        }

        // Initialize pool
        PoolStateMachine(int fee) {
            assert (fee >= 0 && fee <= 100);
            this.fee = fee;

            currentCRR = Decimal.ZERO;
            prevCRR = Decimal.ZERO;

            delegations = new HashMap<>();
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

            settledRewards.put(delegator, rewards + settledRewards.getOrDefault(delegator, 0L));

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

        /**
         * Withdraw is all or nothing, since that is both simpler, implementation-wise and does not make
         * much sense for people to partially withdraw. The problem we run into is that if the amount requested
         * for withdraw, can be less than the amount settled, in which case, it's not obvious if we should perform
         * a settlement ("leave") or save on gas and just withdraw out the rewards.
         */
        public void onWithdraw(Address delegator, long blockNumber) {
            if (delegations.containsKey(delegator)) {
                // do a "leave-and-join"
                long unbondedStake = leave(delegator, blockNumber);
                join(delegator, blockNumber, unbondedStake);
            }

            // if I don't see a delegation, then you must have been settled already.

            // now that all rewards owed to you are settled, you can withdraw them all at once
            long rewards = settledRewards.getOrDefault(delegator, 0L);
            settledRewards.remove(delegator);

            withdrawnRewards.put(delegator, rewards + withdrawnRewards.getOrDefault(delegator, 0L));
            outstandingRewards -= rewards;
        }

        public long onWithdrawOperator() {
            long c = accumulatedCommission;
            accumulatedCommission = 0;

            withdrawnCommission += c;
            outstandingRewards -= c;

            return c;
        }

        /**
         * On block production, we need to withhold the pool commission, and update the rewards managed by this pool.
         * ref: https://github.com/cosmos/cosmos-sdk/blob/master/x/distribution/keeper/allocation.go
         */
        public void onBlock(long blockNumber, long blockReward) {
            assert (blockNumber > 0 && blockReward > 0); // sanity check

            accumulatedBlockRewards += blockReward;
        }
    }

    @Override
    public Reward computeRewards(List<Event> events, int fee) {
        PoolStateMachine sm = new PoolStateMachine(fee);
        Set<Address> addresses = new HashSet<>();

        assert (events.size() > 0);

        // to remember the last block seen, for owed calculation at the end
        long blockNumber = events.get(0).blockNumber;
        for (Event event : events) {
            Address delegator = event.source;
            blockNumber = event.blockNumber;
            Long amount = event.amount;

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
                    //noinspection ConstantConditions
                    assert (amount == null);
                    sm.onWithdraw(delegator, blockNumber);
                    break;
                }
                case BLOCK: {
                    assert (delegator == null);
                    sm.onBlock(blockNumber, amount);
                    break;
                }
            }
        }

        Reward r = new Reward();

        // finalize the owed + withdrawn rewards
        Map<Address, Long> delegatorRewards = new HashMap<>();
        for (Address a : addresses) {
            sm.onWithdraw(a, blockNumber);
            delegatorRewards.put(a, sm.getWithdrawnRewards(a));
        }

        // have the pool operator withdraw as well :)
        sm.onWithdrawOperator();

        r.delegatorRewards = delegatorRewards;
        r.outstandingRewards = sm.outstandingRewards;
        r.operatorRewards = sm.withdrawnCommission;

        return r;
    }
}
