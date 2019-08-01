package org.aion.unity.distribution.f1.model;

/**
 * Stores some "starting information" for a delegation:
 * 1. Previous period (to track the new period this delegation created)
 * 2. How much stake was delegated
 * 3. Block number when stake was delegated
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class DelegatorStartingInfo {
    public long previousPeriod;    // period at which the delegation should withdraw starting from
    public double stake;             // amount of coins being delegated
    public long blockNumber;       // block number at which delegation was created

    public DelegatorStartingInfo(long previousPeriod, double stake, long blockNumber) {
        this.previousPeriod = previousPeriod;
        this.stake = stake;
        this.blockNumber = blockNumber;
    }
}
