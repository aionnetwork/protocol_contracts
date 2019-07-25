package org.aion.unity;

import avm.Address;

import java.util.List;
import java.util.Map;

public abstract class RewardsManager {

    public enum EventType {
        VOTE, UNVOTE, WITHDRAW, BLOCK
    }

    public static class Event {
        public EventType type;
        public Address source;
        public long blockNumber;
        public long amount;

        public Event(EventType type, Address source, long blockNumber, long amount) {
            this.type = type;
            this.source = source;
            this.blockNumber = blockNumber;
            this.amount = amount;
        }

        @Override
        public String toString() {
            return "Event{" +
                    "type=" + type +
                    ", source=" + source +
                    ", blockNumber=" + blockNumber +
                    ", amount=" + amount +
                    '}';
        }
    }

    /**
     * Compute the final rewards of all delegators.
     *
     * @param events a list of user operations
     * @return the settled rewards of all delegators
     */
    public abstract Map<Address, Long> computeRewards(List<Event> events);
}
