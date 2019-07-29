package org.aion.unity;

import avm.Address;

import java.util.*;

// NOTE: this code is for simulation purpose, not for production.

public class DPRewardsManager extends RewardsManager {

    private static final boolean DEBUG = false;

    private static final boolean AUTO_SETTLEMENT = false;
    private static final long MAX_IDLE_PERIOD = 6 * 60 * 24;
    private static final int MAX_ACCOUNTS_TO_SETTLE = 10;

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
        long unsettledShares;
        long unsettledRewards;
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

            long shares = stake * (lastBlockProduced - pair.first);
            long rewards = (unsettledShares == 0) ? unsettledRewards : unsettledRewards * shares / unsettledShares;
            settledRewards.put(delegator, rewards + settledRewards.getOrDefault(delegator, 0L));

            totalStake -= stake;
            unsettledShares -= shares;
            unsettledRewards -= rewards;
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

            unsettledShares += totalStake * (blockNumber - lastBlockProduced);
            unsettledRewards += blockRewards;
            lastBlockProduced = blockNumber;

            if (AUTO_SETTLEMENT) {
                Iterator<Map.Entry<Address, Pair<Long, Long>>> itr = delegators.entrySet().iterator();
                List<Address> addresses = new ArrayList<>();
                while (addresses.size() < MAX_ACCOUNTS_TO_SETTLE && itr.hasNext()) {
                    addresses.add(itr.next().getKey());
                }
                for (Address address : addresses) {
                    Pair<Long, Long> p = delegators.get(address);
                    if (blockNumber - p.first > MAX_IDLE_PERIOD) {
                        onLeave(address, p.second);
                        onJoin(address, blockNumber, p.second);
                    } else {
                        break;
                    }
                }
            }
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
