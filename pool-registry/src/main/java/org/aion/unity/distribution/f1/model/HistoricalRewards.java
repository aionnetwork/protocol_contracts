package org.aion.unity.distribution.f1.model;

/**
 * Historical Reward Value
 *
 * The cumulative rewards ratio is the reward a delegator with 1 stake unit for a given pool would be entitled to
 * at this period, if it had delegated that 1 unit of stake at the zeroth period.
 *
 * Reference count indicates the number of objects which might need to reference this historical entry at any point.
 * It is equal to { the number of outstanding delegations which ended the associated period (and might need to
 * read that record) } + { one per pool for the zeroth period, set on initialization }.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class HistoricalRewards {
    public double cumulativeRewardRatio;
    public int referenceCount;

    public HistoricalRewards(double value) {
        this.cumulativeRewardRatio = value;
        this.referenceCount = 1; // always initialize with a reference count of 1
    }
}
