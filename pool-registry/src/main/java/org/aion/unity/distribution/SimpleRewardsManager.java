package org.aion.unity.distribution;

import avm.Address;
import org.aion.unity.distribution.RewardsManager;

import java.util.*;

public class SimpleRewardsManager extends RewardsManager {

    private long totalStake = 0L;
    private long rewardOutstanding = 0L;
    private Map<Address, Long> stakeMap = new HashMap<>();
    private Map<Address, Long> pendingRewardMap = new HashMap<>();
    private Map<Address, Long> withdrawnRewardMap = new HashMap<>();

    public Map<Address, Long> computeRewards(List<Event> events) throws RuntimeException {
        // ASSUMPTIONS:
        // 1. all events are sorted by block number
        // 2. the block event is the last event for that particular block
        // 3. event amount cannot be negative
        // 4. vote events should not overflow long (not checked)
        // 3. un-votes should not make a stake delegation negative
        // 6. withdraw should not be called if no rewards exist
        // 7. withdraw should not make my rewards balance negative

        ListIterator<Event> itr = events.listIterator();

        while (itr.hasNext()) {
            Event x = itr.next();
            if (x.amount < 0)
                throw new RuntimeException("Event amount is negative.");

            if (itr.hasNext() && events.get(itr.nextIndex()).blockNumber < x.blockNumber)
                throw new RuntimeException("Block numbers are NOT monotonically increasing");

            switch (x.type) {
                case VOTE: {
                    if (stakeMap.containsKey(x.source)) {
                        stakeMap.put(x.source, stakeMap.get(x.source) + x.amount);
                    } else {
                        stakeMap.put(x.source, x.amount);
                    }
                    totalStake += x.amount;
                    break;
                }
                case UNVOTE: {
                    if (!stakeMap.containsKey(x.source))
                        throw new RuntimeException("un-vote event called without any stake delegated.");

                    long ns = stakeMap.get(x.source) - x.amount;
                    if (ns < 0) throw new RuntimeException("Un-vote event made stake balance negative.");
                    stakeMap.put(x.source, ns);

                    totalStake -= x.amount;
                    break;
                }
                case WITHDRAW: {
                    if (!pendingRewardMap.containsKey(x.source))
                        throw new RuntimeException("Withdraw event called without any rewards balance.");

                    long ns = pendingRewardMap.get(x.source) - x.amount;
                    if (ns < 0) throw new RuntimeException("Un-vote event made stake balance negative.");

                    pendingRewardMap.put(x.source, ns);
                    rewardOutstanding -= x.amount;

                    if (withdrawnRewardMap.containsKey(x.source)) {
                        withdrawnRewardMap.put(x.source, withdrawnRewardMap.get(x.source) + x.amount);
                    } else {
                        withdrawnRewardMap.put(x.source, x.amount);
                    }

                    break;
                }
                case BLOCK: {
                    if (itr.hasNext() && events.get(itr.nextIndex()).blockNumber <= x.blockNumber)
                        throw new RuntimeException("Block event is NOT the last event for contained in this block!");

                    // split the rewards for this block between the stakers who contributed to it.
                    long blockReward = x.amount;
                    rewardOutstanding += blockReward;

                    // i need to compute the ratio of what is owed to each of the stakers
                    Map<Address, Float> ratioOwed = new HashMap<>();
                    for (Map.Entry<Address, Long> s : stakeMap.entrySet()) {
                        ratioOwed.put(s.getKey(), (float)s.getValue()/totalStake);
                    }

                    for (Map.Entry<Address, Float> r : ratioOwed.entrySet()) {
                        long stakerReward = (long) Math.floor(blockReward * r.getValue());
                        if (pendingRewardMap.containsKey(r.getKey())) {
                            pendingRewardMap.put(r.getKey(), pendingRewardMap.get(r.getKey()) + stakerReward);
                        } else {
                            pendingRewardMap.put(r.getKey(), stakerReward);
                        }
                    }
                    break;
                }
                default:
                    throw new RuntimeException("Event type not recognized.");
            }
        }

        Map<Address, Long> toReturnMap = new HashMap<>();
        pendingRewardMap.forEach((k, v) -> {
            if (withdrawnRewardMap.containsKey(k))
                toReturnMap.put(k, v + withdrawnRewardMap.get(k));
            else
                toReturnMap.put(k, v);
        });
        return toReturnMap;
    }

    /**
     * @return clone of the stakeMap (local state)
     */
    public Map<Address, Long> getStakeMap() {
        return new HashMap<>(stakeMap);
    }

    /**
     * @return clone of the pendingRewardMap (local state)
     */
    public Map<Address, Long> getPendingRewardMap() {
        return new HashMap<>(pendingRewardMap);
    }

    /**
     * @return clone of the withdrawnRewardMap (local state)
     */
    public Map<Address, Long> getWithdrawnRewardMap() {
        return new HashMap<>(withdrawnRewardMap);
    }

    /**
     * @return clone of the pendingRewardMap (local state)
     */
    public long getRewardOutstanding() {
        return rewardOutstanding;
    }
}
