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

    int commissionRate; // TODO: add max commission rate?
    byte[] metaDataUrl;
    byte[] metaDataContentHash;

    // TODO: opportunity for optimization, duplicate delegator map
    PoolRewardsStateMachine rewards;
    Map<Address, BigInteger> delegators;
    Map<Address, Integer> autoRedelegationDelegators;

    public PoolState(Address stakerAddress, Address coinbaseAddress, Address custodianAddress, int commissionRate, byte[] metaDataUrl, byte[] metaDataContentHash) {
        this.isActive = false;
        this.stakerAddress = stakerAddress;
        this.coinbaseAddress = coinbaseAddress;
        this.custodianAddress = custodianAddress;
        this.commissionRate = commissionRate;

        this.metaDataUrl = metaDataUrl;
        this.metaDataContentHash = metaDataContentHash;

        this.rewards = new PoolRewardsStateMachine(0);
        this.delegators = new AionMap<>();
        this.autoRedelegationDelegators = new AionMap<>();
    }
}
