package org.aion.unity.distribution;

import avm.Address;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public abstract class RewardsManager {

    public static class Reward {
        public Map<Address, Long> delegatorRewards;
        public long outstandingRewards;
        public long operatorRewards;

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();

            delegatorRewards.forEach((k, v) -> {
                b.append(new BigInteger(k.toString(), 16).toString());
                b.append(": ");
                b.append(v.toString() + ", ");
            });
            b.append("\n");

            b.append("Outstanding Coins: " + outstandingRewards + "\n");
            b.append("Operator Rewards: " + operatorRewards);

            return b.toString();
        }
    }

    public enum EventType {
        VOTE, UNVOTE, WITHDRAW, BLOCK
    }

    @SuppressWarnings("WeakerAccess")
    public static class Event {
        public EventType type;
        public Address source;
        public long blockNumber;
        public Long amount;

        public Event(EventType type, Address source, long blockNumber, Long amount) {
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
     * Compute the final rewards for all delegators.
     *
     * @param events a list of user operations
     * @return both pending and withdrawn rewards
     */
    public abstract Reward computeRewards(List<Event> events, int poolFee);


}
