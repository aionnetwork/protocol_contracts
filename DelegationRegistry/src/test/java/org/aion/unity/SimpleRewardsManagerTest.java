package org.aion.unity;

import avm.Address;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.aion.unity.RewardsManager.Event;
import static org.aion.unity.RewardsManager.EventType;
import static org.junit.Assert.fail;

public class SimpleRewardsManagerTest {

    private Address addressOf(int n) {
        byte[] b = new byte[32];
        b[0] = (byte) n;
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
        final long REWARD = 5000;

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

        //RewardsManager dp = new DPRewardsManager();
        //Map<Address, Long> r1 = dp.computeRewards(v);

        System.out.println("testVector001Simple");
    }

    private int getRandomInt(int min, int max) {
        return (int)(Math.random() * max) + min;
    }

    private boolean coinWithProbability(float probability) {
        return (new Random().nextFloat() < probability);
    }

    @Test
    public void testVectorAutogen() {
        // generate test vector
        List<Event> v = new ArrayList<>();

        // system params
        int users = 5;
        long maxUserBalance = 25000;
        long startBlock = 1;
        long endBlock = 100;
        int maxActionsPerBlock = 3;
        long blockReward = 5000;
        float poolProbability = 0.8f; // pool's probability of winning a block.

        // abuse the simple rewards manager a little bit, by running it every time we add a new entry,
        // to see what we can do next. probably a much better way to do this, but whatever ...
        for(long i = startBlock; i <= endBlock; i++) {

            // pick a certain number of actions to do in this round
            int numActions = getRandomInt(0, maxActionsPerBlock);
            for (int j = 0; j < numActions; j++) {

                SimpleRewardsManager rm = new SimpleRewardsManager();
                rm.computeRewards(v);

                // choose a random user
                Address user = addressOf(getRandomInt(1, users));

                // choose a random action to do for the user
                if (rm.getStakeMap().get(user) > 0) {

                }


            }

            // decide if this block will be produced by this pool
            if (coinWithProbability(poolProbability)) {
                v.add(new Event(EventType.BLOCK, null, i, blockReward));
            }
        }

        RewardsManager simple = new SimpleRewardsManager();
        Map<Address, Long> r0 = simple.computeRewards(v);

        //RewardsManager dp = new DPRewardsManager();
        //Map<Address, Long> r1 = dp.computeRewards(v);

        System.out.println("testVector001Simple");
    }


}
