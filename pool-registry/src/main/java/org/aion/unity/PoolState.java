package org.aion.unity;

import avm.Address;
import org.aion.avm.userlib.AionMap;

import java.math.BigInteger;
import java.util.Map;

/**
 * Manages the state of a pool.
 */
public class PoolState {

    public boolean isActive;

    Address stakerAddress;
    Address coinbaseAddress;

    byte[] metaData;
    int commissionRate; // TODO: add max commission rate?

    // TODO: opportunity for optimization, duplicate delegator map
    PoolRewardsStateMachine rewards;
    Map<Address, BigInteger> delegators;

    public PoolState(Address stakerAddress, Address coinbaseAddress, byte[] metaData, int commissionRate) {
        this.isActive = false;
        this.stakerAddress = stakerAddress;
        this.coinbaseAddress = coinbaseAddress;
        this.metaData = metaData;
        this.commissionRate = commissionRate;

        this.rewards = new PoolRewardsStateMachine(0);
        this.delegators = new AionMap<>();
    }
}
