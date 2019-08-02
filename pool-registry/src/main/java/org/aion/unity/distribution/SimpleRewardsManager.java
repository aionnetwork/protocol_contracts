package org.aion.unity.distribution;

import avm.Address;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

public class SimpleRewardsManager extends RewardsManager {

    private long totalStake = 0L;
    private long rewardOutstanding = 0L;
    private Map<Address, Long> stakeMap = new HashMap<>();
    private Map<Address, Long> pendingRewardMap = new HashMap<>();
    private Map<Address, Long> withdrawnRewardMap = new HashMap<>();
    Set<Address> addresses = new HashSet<>();
    private long accumulatedCommission;


    void onWithdraw(Address source) {
        if (!pendingRewardMap.containsKey(source))
            return; // OK

        long remaining = pendingRewardMap.get(source);

        pendingRewardMap.remove(source);
        rewardOutstanding -= remaining;

        withdrawnRewardMap.put(source, withdrawnRewardMap.getOrDefault(source, 0L) + remaining);
    }

    @Override
    public Reward computeRewards(List<Event> events, int fee) {
        // ignore the fee for now ...

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
            if (x.amount != null && x.amount < 0)
                throw new RuntimeException("Event amount is negative.");

            if (itr.hasNext() && events.get(itr.nextIndex()).blockNumber < x.blockNumber)
                throw new RuntimeException("Block numbers are NOT monotonically increasing");

            if (x.type != EventType.BLOCK)
                addresses.add(x.source);

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
                    assert (x.amount == null);
                    onWithdraw(x.source);
                    break;
                }
                case BLOCK: {
                    if (itr.hasNext() && events.get(itr.nextIndex()).blockNumber <= x.blockNumber)
                        throw new RuntimeException("Block event is NOT the last event for contained in this block!");

                    // split the rewards for this block between the stakers who contributed to it.
                    @SuppressWarnings("ConstantConditions") long blockReward = x.amount;

                    // deal with the block rewards
                    double commission = (fee * blockReward) / 100d;
                    double shared = blockReward - commission;

                    accumulatedCommission += commission;
                    rewardOutstanding += blockReward;

                    // i need to compute the ratio of what is owed to each of the stakers
                    Map<Address, Double> ratioOwed = new HashMap<>();
                    for (Map.Entry<Address, Long> s : stakeMap.entrySet()) {
                        ratioOwed.put(s.getKey(), s.getValue() / (double) totalStake);
                    }

                    for (Map.Entry<Address, Double> r : ratioOwed.entrySet()) {
                        long stakerReward = (long) (shared * r.getValue());
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

        Reward r = new Reward();

        // finalize the owed + withdrawn rewards
        Map<Address, Long> delegatorRewards = new HashMap<>();
        for (Address a : addresses) {
            onWithdraw(a);
            delegatorRewards.put(a, withdrawnRewardMap.getOrDefault(a, 0L));
        }

        r.delegatorRewards = delegatorRewards;
        r.outstandingRewards = rewardOutstanding;
        r.operatorRewards = accumulatedCommission;

        return r;
    }

    public Map<Address, Long> getStakeMap() {
        return new HashMap<>(stakeMap);
    }

    public Map<Address, Long> getPendingRewardMap() {
        return new HashMap<>(pendingRewardMap);
    }

    @SuppressWarnings("unused")
    public Map<Address, Long> getWithdrawnRewardMap() {
        return new HashMap<>(withdrawnRewardMap);
    }

    @SuppressWarnings("unused")
    public long getRewardOutstanding() {
        return rewardOutstanding;
    }

    public long getTotalStake() {
        return totalStake;
    }
}
