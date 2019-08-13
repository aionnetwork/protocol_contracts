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

    Address stakerAddress; // a.k.a. owner address
    Address coinbaseAddress;
    Address custodianAddress;

    byte[] metaData;
    int commissionRate; // TODO: add max commission rate?

    // TODO: opportunity for optimization, duplicate delegator map
    PoolRewardsStateMachine rewards;
    Map<Address, BigInteger> delegators;
    Map<Address, Integer> autoRedelegationDelegators;

    public PoolState(Address stakerAddress, Address coinbaseAddress, Address custodianAddress, byte[] metaData, int commissionRate) {
        this.isActive = false;
        this.stakerAddress = stakerAddress;
        this.coinbaseAddress = coinbaseAddress;
        this.custodianAddress = custodianAddress;
        this.metaData = metaData;
        this.commissionRate = commissionRate;

        this.rewards = new PoolRewardsStateMachine(0);
        this.delegators = new AionMap<>();
        this.autoRedelegationDelegators = new AionMap<>();
    }
}
