package org.aion.unity.distribution;

import avm.Address;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.aion.unity.distribution.RewardsManager.Event;
import static org.aion.unity.distribution.RewardsManager.EventType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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

    @Test
    public void testWithdraw() {
        List<Event> events = Arrays.asList(
                new Event(EventType.VOTE, addressOf(1), 3001, 2),
                new Event(EventType.VOTE, addressOf(2), 3003, 3),
                new Event(EventType.BLOCK, addressOf(100), 3005, 5000),
                new Event(EventType.UNVOTE, addressOf(2), 3006, 3),
                new Event(EventType.WITHDRAW, addressOf(2), 3007, 1000),
                new Event(EventType.BLOCK, addressOf(100), 3008, 5000)
        );
        Map<Address, Long> rewards = new DPRewardsManager().computeRewards(events);
        assertEquals(7000L, rewards.get(addressOf(1)).longValue());
        assertEquals(3000L, rewards.get(addressOf(2)).longValue());
    }

    @Ignore
    @Test
    public void testInconstantBlockRewardsSimple() {
        List<Event> events = Arrays.asList(
                new Event(EventType.VOTE, addressOf(1), 3001, 2),
                new Event(EventType.BLOCK, addressOf(100), 3005, 4000),
                new Event(EventType.VOTE, addressOf(2), 3006, 3),
                new Event(EventType.BLOCK, addressOf(100), 3008, 5000)
        );
        Map<Address, Long> rewards = new DPRewardsManager().computeRewards(events);
        assertEquals(4000L + 2000L, rewards.get(addressOf(1)).longValue());
        assertEquals(3000L, rewards.get(addressOf(2)).longValue());
    }

    @Test
    public void testInconstantBlockRewardsDP() {
        List<Event> events = Arrays.asList(
                new Event(EventType.VOTE, addressOf(1), 3001, 2),
                new Event(EventType.BLOCK, addressOf(100), 3002, 4000),
                new Event(EventType.VOTE, addressOf(2), 3003, 3),
                new Event(EventType.BLOCK, addressOf(100), 3004, 5000)
        );
        Map<Address, Long> rewards = new DPRewardsManager().computeRewards(events);
        assertEquals(9000 * (2 * 4) / (2 * 4 + 3 * 2), rewards.get(addressOf(1)).longValue());
        // NOTE: There is a rounding issue below
        // assertEquals(9000 * (3 * 2) / (2 * 4 + 3 * 2), rewards.get(addressOf(2)).longValue());
        assertEquals(9000 - 9000 * (2 * 4) / (2 * 4 + 3 * 2), rewards.get(addressOf(2)).longValue());
    }

    @Test
    public void testDoubleVote() {
        List<Event> events = Arrays.asList(
                new Event(EventType.VOTE, addressOf(1), 3001, 2),
                new Event(EventType.BLOCK, addressOf(100), 3002, 5000),
                new Event(EventType.VOTE, addressOf(1), 3003, 2),
                new Event(EventType.VOTE, addressOf(2), 3003, 3),
                new Event(EventType.BLOCK, addressOf(100), 3004, 5000)
        );
        Map<Address, Long> rewards = new DPRewardsManager().computeRewards(events);
        assertEquals(5000 + 5000 * (4 * 2) / (4 * 2 + 3 * 2), rewards.get(addressOf(1)).longValue());
        assertEquals(10000 - (5000 + 5000 * (4 * 2) / (4 * 2 + 3 * 2)), rewards.get(addressOf(2)).longValue());
    }
}
