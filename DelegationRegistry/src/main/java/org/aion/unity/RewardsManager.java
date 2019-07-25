package org.aion.unity;

import avm.Address;

import java.util.List;
import java.util.Map;

public abstract class RewardsManager {

    public static class Event {
        public String type; // "vote", "unvote", "withdraw"
        public Address delegator;
        public long stake;
        public long blockNumber;

        public Event(String type, Address delegator, long stake, long blockNumber) {
            this.type = type;
            this.delegator = delegator;
            this.stake = stake;
            this.blockNumber = blockNumber;
        }
    }

    public static class Block {
        public long number;
        public long rewards;

        public Block(long number, long rewards) {
            this.number = number;
            this.rewards = rewards;
        }
    }

    /**
     * Compute the final rewards of all delegators.
     *
     * @param events a list of user operations
     * @param blocks a list of blocks
     * @return the rewards of all delegators
     */
    public abstract Map<Address, Long> computeRewards(List<Event> events, List<Block> blocks);
}
