package org.aion.unity.distribution;

import avm.Address;
import org.aion.unity.distribution.RewardsManager;
import org.aion.unity.distribution.SimpleRewardsManager;
import org.junit.Test;

import java.math.BigInteger;
import java.util.*;

import static org.aion.unity.distribution.RewardsManager.Event;
import static org.aion.unity.distribution.RewardsManager.EventType;
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
}
