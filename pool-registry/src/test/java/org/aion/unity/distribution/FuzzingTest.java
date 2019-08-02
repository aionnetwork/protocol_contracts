package org.aion.unity.distribution;

import avm.Address;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.DoubleStream;

import static org.aion.unity.distribution.RewardsManager.Event;
import static org.aion.unity.distribution.RewardsManager.Reward;

public class FuzzingTest {

    @Test
    public void testVectorSimple() {
        final long REWARD = 5_000_000L;
        final int poolFee = 2; // 2%

        // Generate test vector
        List<RewardsManager.Event> events = new ArrayList<>();
        events.add(new RewardsManager.Event(RewardsManager.EventType.VOTE, addressOf(1), 1, 3400L));
        events.add(new RewardsManager.Event(RewardsManager.EventType.VOTE, addressOf(2), 1, 5700L));
        events.add(new RewardsManager.Event(RewardsManager.EventType.VOTE, addressOf(2), 2, 2000L));
        events.add(new RewardsManager.Event(RewardsManager.EventType.BLOCK, null, 2, REWARD));
        events.add(new RewardsManager.Event(RewardsManager.EventType.VOTE, addressOf(3), 3, 6000L));
        events.add(new RewardsManager.Event(RewardsManager.EventType.VOTE, addressOf(4), 4, 1500L));
        events.add(new RewardsManager.Event(RewardsManager.EventType.VOTE, addressOf(5), 4, 6000L));
        events.add(new RewardsManager.Event(RewardsManager.EventType.BLOCK, null, 7, REWARD));
        events.add(new RewardsManager.Event(RewardsManager.EventType.BLOCK, null, 8, REWARD));
        events.add(new RewardsManager.Event(RewardsManager.EventType.BLOCK, null, 11, REWARD));
        events.add(new RewardsManager.Event(RewardsManager.EventType.WITHDRAW, addressOf(2), 12, null));
        events.add(new RewardsManager.Event(RewardsManager.EventType.WITHDRAW, addressOf(3), 12, null));
        events.add(new RewardsManager.Event(RewardsManager.EventType.WITHDRAW, addressOf(4), 12, null));
        events.add(new RewardsManager.Event(RewardsManager.EventType.VOTE, addressOf(5), 12, 1200L));
        events.add(new RewardsManager.Event(RewardsManager.EventType.UNVOTE, addressOf(2), 12, 3000L));
        events.add(new RewardsManager.Event(RewardsManager.EventType.BLOCK, null, 25, REWARD));
        events.add(new RewardsManager.Event(RewardsManager.EventType.WITHDRAW, addressOf(5), 26, null));
        events.add(new RewardsManager.Event(RewardsManager.EventType.BLOCK, null, 32, REWARD));
        events.add(new RewardsManager.Event(RewardsManager.EventType.WITHDRAW, addressOf(4), 36, null));
        events.add(new RewardsManager.Event(RewardsManager.EventType.WITHDRAW, addressOf(5), 36, null));

        printStats(events, poolFee);
    }

    @Test
    public void testVectorAutogen() {
        // generated vector
        List<Event> events = new ArrayList<>();

        // system params
        int users = 1000;
        long maxUserBalance = 25000;
        long startBlock = 1;
        long endBlock = 10000;
        int maxActionsPerBlock = 3;
        long blockReward = 5_000_000L;
        float poolProbability = 0.8f; // pool's probability of winning a block.
        int poolFee = 3; // 3%

        SimpleRewardsManager rm = new SimpleRewardsManager();

        // abuse the simple rewards manager a little bit, by running it every time we add a new entry,
        // to see what we can do next. probably a much better way to do this, but whatever ...
        for (long i = startBlock; i <= endBlock; i++) {

            // temporary list where we accumuate the events in this block
            List<RewardsManager.Event> v = new ArrayList<>();

            Set<Address> usersInThisRound = new HashSet<>();

            // pick a certain number of actions to do in this round
            int numActions = getRandomInt(0, maxActionsPerBlock);
            for (int j = 0; j < numActions; j++) {

                // choose a random user, not already used in this round
                Address user;
                do {
                    user = addressOf(getRandomInt(1, users));
                } while (usersInThisRound.contains(user));
                usersInThisRound.add(user);

                // choose a random action to do for the user ...

                // if the user hasn't staked already start with that
                if (!rm.getStakeMap().containsKey(user)) {
                    v.add(new RewardsManager.Event(RewardsManager.EventType.VOTE, user, i, getRandomLong(1, maxUserBalance)));
                } else {
                    // choose between withdraw, unvote or vote more, with 1/3 probability
                    int choice = getRandomInt(1, 3);
                    long stakedBalance = rm.getStakeMap().getOrDefault(user, 0L);

                    if (choice == 1) { // vote more
                        long remainingBalance = maxUserBalance - stakedBalance;
                        v.add(new RewardsManager.Event(RewardsManager.EventType.VOTE, user, i, getRandomLong(1, remainingBalance)));
                    } else if (choice == 2) { // unvote
                        if (stakedBalance > 0L)
                            v.add(new RewardsManager.Event(RewardsManager.EventType.UNVOTE, user, i, getRandomLong(1, stakedBalance)));
                    } else if (choice == 3 && stakedBalance > 0L) { // withdraw
                        long pendingRewards = rm.getPendingRewardMap().getOrDefault(user, 0L);

                        if (pendingRewards > 0)
                            v.add(new RewardsManager.Event(RewardsManager.EventType.WITHDRAW, user, i, null));
                    }
                }
            }

            // decide if this block will be produced by this pool
            if (coinWithProbability(poolProbability) && rm.getTotalStake() > 0) {
                v.add(new RewardsManager.Event(RewardsManager.EventType.BLOCK, null, i, blockReward));
            }

            // apply the rewards this round to the simple rewards manager
            try {
                rm.computeRewards(v, poolFee);
            } catch (Exception e) {
                System.out.println("Bad events-list constructed.");
            }

            events.addAll(v);
        }

        printStats(events, poolFee);
    }

    private void printStats(List<Event> events, int poolFee) {
        RewardsManager simple = new SimpleRewardsManager();
        Reward r0 = simple.computeRewards(events, poolFee);

        RewardsManager f2 = new F2RewardsManager();
        Reward r2 = f2.computeRewards(events, poolFee);

        System.out.println("Simple Rewards Stats\n---------------------------------");
        System.out.println(r0 + "\n");

        System.out.println("F2 Stats\n---------------------------------");
        System.out.println(r2 + "\n");

        double[] error = calcErrorSD(r0.delegatorRewards, r2.delegatorRewards);

        System.out.println("STD Comparison\n---------------------------------");
        System.out.printf("Error (F2): mean = %.2f%%, sd = %.2f%%\n", error[0], error[1]);
    }

    private Address addressOf(int n) {
        byte[] b = new byte[32];

        byte[] trimmed = BigInteger.valueOf(n).toByteArray();
        int padding = 32 - trimmed.length;

        System.arraycopy(trimmed, 0, b, padding, trimmed.length);

        return new Address(b);
    }

    private int getRandomInt(int min, int max) {
        return (int) (Math.random() * max) + min;
    }

    private long getRandomLong(long min, long max) {
        return (long) (Math.random() * max) + min;
    }

    private boolean coinWithProbability(float probability) {
        return (new Random().nextFloat() < probability);
    }

    // assuming key sets are the same
    private double[] calcErrorSD(Map<Address, Long> base, Map<Address, Long> toCheck) {
        double[] errors = base.keySet().stream().mapToDouble(k -> {
            double v1 = base.getOrDefault(k, 0L);
            double v2 = toCheck.getOrDefault(k, 0L);
            return 100.0 * Math.abs(v2 - v1) / v1;
        }).toArray();


        double mean = DoubleStream.of(errors).sum() / errors.length;
        double sd = Math.sqrt(DoubleStream.of(errors).map(e -> Math.pow(e - mean, 2)).sum() / errors.length);

        return new double[]{mean, sd};
    }
}
