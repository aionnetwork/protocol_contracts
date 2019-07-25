package org.aion.unity;

import avm.Address;
import org.junit.Test;

import static org.aion.unity.RewardsManager.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class DPRewardsManagerTest {


    private Address addressOf(int n) {
        byte[] b = new byte[32];
        b[0] = (byte) n;
        return new Address(b);
    }

    @Test
    public void testSingleUser1() {
        List<Event> events = Arrays.asList(
                new Event(EventType.VOTE, addressOf(1), 3001, 2)
        );
        Map<Address, Long> rewards = new DPRewardsManager().computeRewards(events);
        assertNull(rewards.get(addressOf(1)));
    }

    @Test
    public void testSingleUser2() {
        List<Event> events = Arrays.asList(
                new Event(EventType.VOTE, addressOf(1), 3001, 2),
                new Event(EventType.UNVOTE, addressOf(1), 3002, 2),
                new Event(EventType.BLOCK, addressOf(100), 3003, 5000)
        );
        Map<Address, Long> rewards = new DPRewardsManager().computeRewards(events);
        assertNull(rewards.get(addressOf(1)));
    }

    @Test
    public void testSingleUser3() {
        List<Event> events = Arrays.asList(
                new Event(EventType.VOTE, addressOf(1), 3001, 2),
                new Event(EventType.BLOCK, addressOf(100), 3002, 5000)
        );
        Map<Address, Long> rewards = new DPRewardsManager().computeRewards(events);
        assertEquals(5000L, rewards.get(addressOf(1)).longValue());
    }

    @Test
    public void testSingleUser4() {
        List<Event> events = Arrays.asList(
                new Event(EventType.VOTE, addressOf(1), 3001, 2),
                new Event(EventType.BLOCK, addressOf(100), 3005, 5000)
        );
        Map<Address, Long> rewards = new DPRewardsManager().computeRewards(events);
        assertEquals(5000L, rewards.get(addressOf(1)).longValue());
    }

    @Test
    public void testTwoUsers1() {
        List<Event> events = Arrays.asList(
                new Event(EventType.VOTE, addressOf(1), 3001, 2),
                new Event(EventType.VOTE, addressOf(2), 3001, 3),
                new Event(EventType.BLOCK, addressOf(100), 3005, 5000)
        );
        Map<Address, Long> rewards = new DPRewardsManager().computeRewards(events);
        assertEquals(2000L, rewards.get(addressOf(1)).longValue());
        assertEquals(3000L, rewards.get(addressOf(2)).longValue());
    }

    @Test
    public void testTwoUsers2() {
        List<Event> events = Arrays.asList(
                new Event(EventType.VOTE, addressOf(1), 3001, 2),
                new Event(EventType.VOTE, addressOf(2), 3003, 3),
                new Event(EventType.BLOCK, addressOf(100), 3005, 5000)
        );
        Map<Address, Long> rewards = new DPRewardsManager().computeRewards(events);
        assertEquals(2000L, rewards.get(addressOf(1)).longValue());
        assertEquals(3000L, rewards.get(addressOf(2)).longValue());
    }

    @Test
    public void testTwoUsers3() {
        List<Event> events = Arrays.asList(
                new Event(EventType.VOTE, addressOf(1), 3001, 2),
                new Event(EventType.VOTE, addressOf(2), 3003, 3),
                new Event(EventType.BLOCK, addressOf(100), 3005, 5000),
                new Event(EventType.BLOCK, addressOf(100), 3008, 5000)
        );
        Map<Address, Long> rewards = new DPRewardsManager().computeRewards(events);
        assertEquals(4000L, rewards.get(addressOf(1)).longValue());
        assertEquals(6000L, rewards.get(addressOf(2)).longValue());
    }

    @Test
    public void testTwoUsers4() {
        List<Event> events = Arrays.asList(
                new Event(EventType.VOTE, addressOf(1), 3001, 2),
                new Event(EventType.VOTE, addressOf(2), 3003, 3),
                new Event(EventType.BLOCK, addressOf(100), 3005, 5000),
                new Event(EventType.UNVOTE, addressOf(2), 3006, 3),
                new Event(EventType.BLOCK, addressOf(100), 3008, 5000)
        );
        Map<Address, Long> rewards = new DPRewardsManager().computeRewards(events);
        assertEquals(7000L, rewards.get(addressOf(1)).longValue());
        assertEquals(3000L, rewards.get(addressOf(2)).longValue());
    }
}
