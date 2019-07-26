package org.aion.unity;

import avm.Address;

import java.util.*;

// NOTE: this code is for simulation purpose, not for production.

public class AccRewardsManager extends RewardsManager {

    private static final boolean DEBUG = false;


    private static class Pair<K, V> {
        private K first;
        private V second;

        public Pair(K first, V second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public String toString() {
            return "[" + first + "," + second + "]";
        }
    }

    private static class PoolStateMachine {
        // <block number, stake>
        Map<Address, Pair<Long, Long>> delegators = new LinkedHashMap<>();
        long totalStake;
        Map<Long, Long> accumulatedRewardsPerStake = new HashMap<>();
        long lastBlockProduced = -1;
        Map<Address, Long> settledRewards = new HashMap<>();
        Map<Address, Long> withdrawnRewards = new HashMap<>();

        public Set<Address> getDelegators() {
            return new HashSet<>(delegators.keySet());
        }

        public long getStake(Address delegator) {
            return delegators.containsKey(delegator) ? delegators.get(delegator).second : 0L;
        }

        public long getSettledRewards(Address delegator) {
            return settledRewards.getOrDefault(delegator, 0L);
        }

        public long getWithdrawnRewards(Address delegator) {
            return withdrawnRewards.getOrDefault(delegator, 0L);
        }

        // in-block
        public void onJoin(Address delegator, long blockNumber, long stake) {
            assert (delegator != null);
            assert (blockNumber > 0);
            assert (stake > 0);

            // initialize last block number
            if (lastBlockProduced == -1) {
                lastBlockProduced = blockNumber - 1;
            }

            // do not allow double-join
            if (delegators.containsKey(delegator)) {
                throw new IllegalStateException("Delegator already exist");
            }


            // NOTE: we're using the `lastBlockProduced`, rather than the actual block number,
            // mainly because the `unsettledShares` is based on it.
            totalStake += stake;
            delegators.put(delegator, new Pair(lastBlockProduced, stake));
        }

        // in-block
        public void onLeave(Address delegator, long stake) {
            assert (delegator != null);
            assert (stake > 0);

            // only allow to leave with all stake
            Pair<Long, Long> pair = delegators.get(delegator);
            if (pair == null || pair.second != stake) {
                throw new IllegalArgumentException("Invalid stake to leave");
            }

            long startAccumulatedRewardsPerStake = accumulatedRewardsPerStake.getOrDefault(pair.first, 0L);
            long endAccumulatedRewardsPerStake = accumulatedRewardsPerStake.getOrDefault(lastBlockProduced, 0L);
            long rewards = (endAccumulatedRewardsPerStake - startAccumulatedRewardsPerStake) * pair.second;
            settledRewards.put(delegator, rewards + settledRewards.getOrDefault(delegator, 0L));

            totalStake -= stake;
            delegators.remove(delegator);
        }

        public long onWithdraw(Address delegator, long limit) {
            assert (delegator != null);
            assert (limit > 0);

            long rewards = settledRewards.getOrDefault(delegator, 0L);
            long withdrawn = Math.min(limit, rewards);
            long remaining = rewards - withdrawn;
            if (remaining == 0) {
                settledRewards.remove(delegator);
            } else {
                settledRewards.put(delegator, remaining);
            }

            withdrawnRewards.put(delegator, withdrawn + withdrawnRewards.getOrDefault(delegator, 0L));
            return withdrawn;
        }

        // post-block
        public void onBlock(long blockNumber, long blockRewards) {
            assert (blockNumber > 0);
            assert (blockRewards > 0);

            // precision lost
            assert (totalStake > 0);
            long rewardsPerStake = blockRewards / totalStake;
            accumulatedRewardsPerStake.put(blockNumber, accumulatedRewardsPerStake.getOrDefault(lastBlockProduced, 0L) + rewardsPerStake);
            lastBlockProduced = blockNumber;
        }
    }

    // assuming events are in chronological order

    @Override
    public Map<Address, Long> computeRewards(List<Event> events) {
        PoolStateMachine psm = new PoolStateMachine();
        Set<Address> addresses = new HashSet<>();

        for (Event event : events) {
            if (DEBUG) {
                System.out.println(event);
            }

            switch (event.type) {
                case VOTE: {
                    addresses.add(event.source);
                    long stake = psm.getStake(event.source);
                    if (stake != 0) {
                        psm.onLeave(event.source, stake);
                    }
                    psm.onJoin(event.source, event.blockNumber, stake + event.amount);
                    // TODO: call stake registry to vote
                    break;
                }
                case UNVOTE: {
                    addresses.add(event.source);
                    long stake = psm.getStake(event.source);
                    psm.onLeave(event.source, stake);
                    if (stake != event.amount) {
                        psm.onJoin(event.source, event.blockNumber, stake - event.amount);
                    }
                    // TODO: call stake registry to unvote
                    break;
                }
                case WITHDRAW: {
                    addresses.add(event.source);
                    long stake = psm.getStake(event.source);
                    if (stake != 0) {
                        psm.onLeave(event.source, stake);
                        psm.onJoin(event.source, event.blockNumber, stake);
                    }
                    psm.onWithdraw(event.source, event.amount);
                    // TODO: add a transfer
                    break;
                }
                case BLOCK: {
                    psm.onBlock(event.blockNumber, event.amount);
                    break;
                }
            }
        }

        // finalize
        for (Address delegator : psm.getDelegators()) {
            psm.onLeave(delegator, psm.getStake(delegator));
        }

        // lookup all known addresses
        Map<Address, Long> rewards = new HashMap<>();
        for (Address a : addresses) {
            long r = psm.getSettledRewards(a) + psm.getWithdrawnRewards(a);
            if (r != 0) {
                rewards.put(a, r);
            }
        }

        return rewards;
    }
}
