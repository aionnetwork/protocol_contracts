package org.aion.unity;

import avm.Address;

import java.util.*;

// NOTE: this code is for simulation purpose, not for production.

public class DPRewardsManager extends RewardsManager {

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
        Map<Address, Pair<Long, Long>> delegators = new HashMap<>();
        long totalStake;
        long unsettledShares;
        long unsettledRewards;
        long lastUpdate;
        Map<Address, Long> settledRewards = new HashMap<>();

        public Set<Address> getDelegators() {
            return new HashSet<>(delegators.keySet());
        }

        public long getStake(Address delegator) {
            return delegators.containsKey(delegator) ? delegators.get(delegator).second : 0L;
        }

        public long getRewards(Address delegator) {
            return settledRewards.getOrDefault(delegator, 0L);
        }

        // in-block
        public void onJoin(Address delegator, long blockNumber, long stake) {
            assert (delegator != null);
            assert (blockNumber > 0);
            assert (stake > 0);

            if (lastUpdate == 0) {
                lastUpdate = blockNumber;
            }

            Pair<Long, Long> pair = delegators.get(delegator);
            if (pair != null) {
                onLeave(delegator, pair.second);
            }


            // NOTE: we're using the `lastUpdate`, rather than the actual block number,
            // mainly because the `unsettledShare` is based on that number.
            totalStake += stake;
            delegators.put(delegator, new Pair(lastUpdate, stake));
        }

        // in-block
        public void onLeave(Address delegator, long stake) {
            assert (delegator != null);
            assert (stake > 0);

            Pair<Long, Long> pair = delegators.get(delegator);
            if (pair == null || pair.second != stake) {
                throw new IllegalArgumentException("Invalid stake");
            }

            long shares = stake * (lastUpdate - pair.first);
            long rewards = (unsettledShares == 0) ? unsettledRewards : unsettledRewards * shares / unsettledShares;
            settledRewards.put(delegator, rewards + settledRewards.getOrDefault(delegator, 0L));

            totalStake -= stake;
            unsettledShares -= shares;
            unsettledRewards -= rewards;
            delegators.remove(delegator);
        }

        public void onWithdraw(Address delegator, long limit) {
            assert (delegator != null);
            assert (limit > 0);

            long rewards = settledRewards.getOrDefault(delegator, 0L);
            long remaining = rewards - Math.max(limit, rewards);
            if (remaining == 0) {
                settledRewards.remove(delegator);
            } else {
                settledRewards.put(delegator, remaining);
            }
        }

        // post-block
        public void onBlock(long blockNumber, long blockRewards) {
            assert (blockNumber > 0);
            assert (blockRewards > 0);

            unsettledShares += totalStake * (blockNumber - lastUpdate);
            unsettledRewards += blockRewards;
            lastUpdate = blockNumber;
        }
    }

    // assuming events are in chronological order

    @Override
    public Map<Address, Long> computeRewards(List<Event> events) {
        PoolStateMachine psm = new PoolStateMachine();
        Set<Address> addresses = new HashSet<>();

        for (Event event : events) {
            switch (event.type) {
                case VOTE:
                    addresses.add(event.source);
                    psm.onJoin(event.source, event.blockNumber, event.amount);
                    // TODO: call stake registry to vote
                    break;
                case UNVOTE:
                    addresses.add(event.source);
                    psm.onLeave(event.source, event.amount);
                    // TODO: call stake registry to unvote
                    break;
                case WITHDRAW:
                    addresses.add(event.source);
                    psm.onLeave(event.source, psm.getStake(event.source));
                    psm.onJoin(event.source, event.blockNumber, psm.getStake(event.source));
                    psm.onWithdraw(event.source, event.amount);
                    // TODO: add a transfer
                    break;
                case BLOCK:
                    psm.onBlock(event.blockNumber, event.amount);
                    break;
            }
        }

        // finalize
        for (Address delegator : psm.getDelegators()) {
            psm.onLeave(delegator, psm.getStake(delegator));
        }

        // lookup all known addresses
        Map<Address, Long> rewards = new HashMap<>();
        for (Address a : addresses) {
            long r = psm.getRewards(a);
            if (r != 0) {
                rewards.put(a, r);
            }
        }

        return rewards;
    }
}
