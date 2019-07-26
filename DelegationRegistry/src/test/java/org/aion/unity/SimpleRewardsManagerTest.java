package org.aion.unity;

import avm.Address;
import org.junit.Test;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.DoubleStream;

import static org.aion.unity.RewardsManager.Event;
import static org.aion.unity.RewardsManager.EventType;
import static org.junit.Assert.fail;

public class SimpleRewardsManagerTest {

    private Address addressOf(int n) {
        byte[] b = new byte[32];

        byte[] trimmed = BigInteger.valueOf(n).toByteArray();
        int padding = 32 - trimmed.length;

        System.arraycopy(trimmed, 0, b, padding, trimmed.length);

        return new Address(b);
    }


    @Test(expected = RuntimeException.class)
    public void testBadInput1() {
        // Order of blocks is wrong. Should throw a runtime exception.
        List<Event> v = new ArrayList<>();
        v.add(new Event(EventType.VOTE, addressOf(0), 1, 1));
        v.add(new Event(EventType.VOTE, addressOf(1), 2, 1));
        v.add(new Event(EventType.VOTE, addressOf(2), 3, 1));
        v.add(new Event(EventType.VOTE, addressOf(3), 1, 1));

        RewardsManager rm = new SimpleRewardsManager();
        rm.computeRewards(v);
    }

    @Test
    public void testBadInput2() {
        // Order of blocks is wrong. Should throw a runtime exception.
        List<Event> v = new ArrayList<>();
        v.add(new Event(EventType.VOTE, addressOf(0), 1, 1));
        v.add(new Event(EventType.VOTE, addressOf(1), 2, 1));
        v.add(new Event(EventType.BLOCK, null, 3, 1));
        v.add(new Event(EventType.VOTE, addressOf(2), 3, 1));

        RewardsManager rm = new SimpleRewardsManager();
        boolean a = true;
        try {
            a = false;
            rm.computeRewards(v);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        if (a) fail("Exception not thrown");
    }


    @Test
    public void testVector001Simple() {
        final long REWARD = 50_000_000;

        // Generate test vector
        List<Event> v = new ArrayList<>();
        v.add(new Event(EventType.VOTE, addressOf(1), 1, 3400));
        v.add(new Event(EventType.VOTE, addressOf(2), 1, 5700));
        v.add(new Event(EventType.VOTE, addressOf(2), 2, 2000));
        v.add(new Event(EventType.BLOCK, null, 2, REWARD));
        v.add(new Event(EventType.VOTE, addressOf(3), 3, 6000));
        v.add(new Event(EventType.VOTE, addressOf(4), 4, 1500));
        v.add(new Event(EventType.VOTE, addressOf(5), 4, 6000));
        v.add(new Event(EventType.BLOCK, null, 7, REWARD));
        v.add(new Event(EventType.BLOCK, null, 8, REWARD));
        v.add(new Event(EventType.BLOCK, null, 11, REWARD));
        v.add(new Event(EventType.WITHDRAW, addressOf(2), 12, 8000));
        v.add(new Event(EventType.WITHDRAW, addressOf(3), 12, 3500));
        v.add(new Event(EventType.WITHDRAW, addressOf(4), 12, 500));
        v.add(new Event(EventType.VOTE, addressOf(5), 12, 1200));
        v.add(new Event(EventType.UNVOTE, addressOf(2), 12, 3000));
        v.add(new Event(EventType.BLOCK, null, 25, REWARD));
        v.add(new Event(EventType.WITHDRAW, addressOf(5), 26, 5235));
        v.add(new Event(EventType.BLOCK, null, 32, REWARD));
        v.add(new Event(EventType.WITHDRAW, addressOf(4), 36, 1068));
        v.add(new Event(EventType.WITHDRAW, addressOf(5), 36, 1578));

        RewardsManager simple = new SimpleRewardsManager();
        Map<Address, Long> r0 = simple.computeRewards(v);

        RewardsManager acc = new AccRewardsManager();
        Map<Address, Long> r1 = acc.computeRewards(v);

        System.out.println(r0);
        System.out.println(r1);
        double[] error = calcErrorSD(r0, r1);
        System.out.printf("Error: mean = %.2f%%, sd = %.2f%%\n", error[0], error[1]);
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

    @Test
    public void testVectorAutogen() {
        // generated vector
        List<Event> q = new ArrayList<>();

        // system params
        int users = 1000;
        long maxUserBalance = 25000;
        long startBlock = 1;
        long endBlock = 10000;
        int maxActionsPerBlock = 3;
        long blockReward = 5_000_000L;
        float poolProbability = 0.8f; // pool's probability of winning a block.

        SimpleRewardsManager rm = new SimpleRewardsManager();

        // abuse the simple rewards manager a little bit, by running it every time we add a new entry,
        // to see what we can do next. probably a much better way to do this, but whatever ...
        for (long i = startBlock; i <= endBlock; i++) {

            // temporary list where we accumuate the events in this block
            List<Event> v = new ArrayList<>();

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
                    v.add(new Event(EventType.VOTE, user, i, getRandomLong(1, maxUserBalance)));
                } else {
                    // choose between withdraw, unvote or vote more, with 1/3 probability
                    int choice = getRandomInt(1, 3);
                    Long stakedBalance = rm.getStakeMap().get(user);
                    if (stakedBalance == null) stakedBalance = 0L;

                    if (choice == 1) { // vote more
                        long remainingBalance = maxUserBalance - stakedBalance;
                        v.add(new Event(EventType.VOTE, user, i, getRandomLong(1, remainingBalance)));
                    } else if (choice == 2) { // unvote
                        if (stakedBalance > 0L)
                            v.add(new Event(EventType.UNVOTE, user, i, getRandomLong(1, stakedBalance)));
                    } else if (choice == 3 && stakedBalance > 0L) { // withdraw
                        Long pendingRewards = rm.getPendingRewardMap().get(user);
                        if (pendingRewards == null) pendingRewards = 0L;

                        // with half probability, withdraw all or some fraction
                        boolean withdrawAll = coinWithProbability(0.5f);
                        if (pendingRewards > 0) {
                            if (withdrawAll) {
                                v.add(new Event(EventType.WITHDRAW, user, i, pendingRewards));
                            } else {
                                v.add(new Event(EventType.WITHDRAW, user, i, getRandomLong(1, pendingRewards)));
                            }
                        }
                    }
                }
            }

            // decide if this block will be produced by this pool
            if (coinWithProbability(poolProbability)) {
                v.add(new Event(EventType.BLOCK, null, i, blockReward));
            }

            // apply the rewards this round to the simple rewards manager
            try {
                rm.computeRewards(v);
            } catch (Exception e) {
                System.out.println("Bad events-list constructed.");
            }

            q.addAll(v);
        }

        RewardsManager simple = new SimpleRewardsManager();
        Map<Address, Long> r0 = simple.computeRewards(q);
        System.out.println(r0);

        RewardsManager dp = new DPRewardsManager();
        Map<Address, Long> r1 = dp.computeRewards(q);
        System.out.println(r1);

        double[] error = calcErrorSD(r0, r1);
        System.out.printf("Error: mean = %.2f%%, sd = %.2f%%\n", error[0], error[1]);

        long sum = q.stream().filter(e -> e.type == EventType.BLOCK).mapToLong(e -> e.amount).sum();
        long sum0 = r0.values().stream().mapToLong(v -> v).sum();
        long sum1 = r1.values().stream().mapToLong(v -> v).sum();
        System.out.printf("Sum = [%d, %d], lose = [%.2f%%, %.2f%%]\n", sum0, sum1,
                100.0 * Math.abs(sum0 - sum) / sum, 100.0 * Math.abs(sum1 - sum) / sum);
    }

    @Test
    public void testVectorAutogenWithPreLockout() {
        // generated vector
        List<Event> q = new ArrayList<>();

        // system params
        int users = 1000;
        long maxUserBalance = 25000;
        long startBlock = 1;
        long endBlock = 10000;
        int maxActionsPerBlock = 3;
        long blockReward = 5_000_000L;
        float poolProbability = 0.8f; // pool's probability of winning a block.
        long preLockBlocks = 6 * 60 * 24 * 3;

        SimpleRewardsManager rm = new SimpleRewardsManager();

        Map<Address, Long> isUserLocked = new HashMap<>();

        // abuse the simple rewards manager a little bit, by running it every time we add a new entry,
        // to see what we can do next. probably a much better way to do this, but whatever ...
        for (long i = startBlock; i <= endBlock; i++) {

            // temporary list where we accumuate the events in this block
            List<Event> v = new ArrayList<>();

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
                    v.add(new Event(EventType.VOTE, user, i, getRandomLong(1, maxUserBalance)));
                    isUserLocked.put(user, preLockBlocks);
                } else {
                    // choose between withdraw, unvote or vote more, with 1/3 probability
                    int choice = getRandomInt(1, 3);
                    Long stakedBalance = rm.getStakeMap().get(user);
                    if (stakedBalance == null) stakedBalance = 0L;

                    if (choice == 1) { // vote more
                        long remainingBalance = maxUserBalance - stakedBalance;
                        v.add(new Event(EventType.VOTE, user, i, getRandomLong(1, remainingBalance)));
                        isUserLocked.put(user, preLockBlocks);
                    } else if (choice == 2 && isUserLocked.get(user) <= 0) { // unvote
                        if (stakedBalance > 0L)
                            v.add(new Event(EventType.UNVOTE, user, i, getRandomLong(1, stakedBalance)));
                    } else if (choice == 3 && stakedBalance > 0L && isUserLocked.get(user) <= 0) { // withdraw
                        Long pendingRewards = rm.getPendingRewardMap().get(user);
                        if (pendingRewards == null) pendingRewards = 0L;

                        // with half probability, withdraw all or some fraction
                        boolean withdrawAll = coinWithProbability(0.5f);
                        if (pendingRewards > 0) {
                            if (withdrawAll) {
                                v.add(new Event(EventType.WITHDRAW, user, i, pendingRewards));
                            } else {
                                v.add(new Event(EventType.WITHDRAW, user, i, getRandomLong(1, pendingRewards)));
                            }
                        }
                    }
                }
            }

            // decide if this block will be produced by this pool
            if (coinWithProbability(poolProbability)) {
                v.add(new Event(EventType.BLOCK, null, i, blockReward));
            }

            // apply the rewards this round to the simple rewards manager
            try {
                rm.computeRewards(v);
            } catch (Exception e) {
                System.out.println("Bad events-list constructed.");
            }

            q.addAll(v);

            // decrement the "pre-lock" period for the user to make a withdrawal or unvote
            isUserLocked.forEach((x, y) -> {
                isUserLocked.put(x,  y-1 < 0 ? 0 : y-1);
            });
        }

        RewardsManager simple = new SimpleRewardsManager();
        Map<Address, Long> r0 = simple.computeRewards(q);
        System.out.println(r0);

        RewardsManager dp = new DPRewardsManager();
        Map<Address, Long> r1 = dp.computeRewards(q);
        System.out.println(r1);

        double[] error = calcErrorSD(r0, r1);
        System.out.printf("Error: mean = %.2f%%, sd = %.2f%%\n", error[0], error[1]);

        long sum = q.stream().filter(e -> e.type == EventType.BLOCK).mapToLong(e -> e.amount).sum();
        long sum0 = r0.values().stream().mapToLong(v -> v).sum();
        long sum1 = r1.values().stream().mapToLong(v -> v).sum();
        System.out.printf("Sum = [%d, %d], lose = [%.2f%%, %.2f%%]\n", sum0, sum1,
                100.0 * Math.abs(sum0 - sum) / sum, 100.0 * Math.abs(sum1 - sum) / sum);
    }

    // assuming key sets are the same
    private double[] calcErrorSD(Map<Address, Long> base, Map<Address, Long> toCheck) {
        double[] errors = base.keySet().stream().mapToDouble(k -> {
            Long v1 = base.getOrDefault(k, 0L);
            Long v2 = toCheck.getOrDefault(k, 0L);
            return 100.0 * Math.abs(v2 - v1) / v1;
        }).toArray();


        double mean = DoubleStream.of(errors).sum() / errors.length;
        double sd = Math.sqrt(DoubleStream.of(errors).map(e -> Math.pow(e - mean, 2)).sum() / errors.length);

        return new double[]{mean, sd};
    }
}
