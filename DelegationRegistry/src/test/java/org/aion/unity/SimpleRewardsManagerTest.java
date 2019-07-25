package org.aion.unity;

import avm.Address;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.aion.unity.RewardsManager.Event;
import static org.aion.unity.RewardsManager.EventType;
import static org.junit.Assert.fail;

public class SimpleRewardsManagerTest {

    private Address addressOf(int n) {
        byte[] b = new byte[32];
        b[0] = (byte) n;
        return new Address(b);
    }

    final long REWARD = 5000;

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
        v.add(new Event(EventType.WITHDRAW, addressOf(5), 26, 5000));

        RewardsManager simple = new SimpleRewardsManager();
        Map<Address, Long> r0 = simple.computeRewards(v);

        RewardsManager dp = new DPRewardsManager();
        Map<Address, Long> r1 = dp.computeRewards(v);

        System.out.println("testVector001Simple");
    }


}
