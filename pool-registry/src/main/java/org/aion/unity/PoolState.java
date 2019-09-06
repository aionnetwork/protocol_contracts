package org.aion.unity;

import avm.Address;
import org.aion.avm.userlib.AionMap;

import java.math.BigInteger;
import java.util.Map;

/**
 * Manages the state of a pool.
 */
public class PoolState {
    Address coinbaseAddress;

    int commissionRate;
    byte[] metaDataUrl;
    byte[] metaDataContentHash;

    // TODO: opportunity for optimization, duplicate delegator map
    PoolRewardsStateMachine rewards;
    Map<Address, BigInteger> delegators;
    Map<Address, Integer> autoRewardsDelegationDelegators;

    public PoolState(Address coinbaseAddress, int commissionRate, byte[] metaDataUrl, byte[] metaDataContentHash) {
        this.coinbaseAddress = coinbaseAddress;
        this.commissionRate = commissionRate;

        this.metaDataUrl = metaDataUrl;
        this.metaDataContentHash = metaDataContentHash;

        this.rewards = new PoolRewardsStateMachine();
        this.delegators = new AionMap<>();
        this.autoRewardsDelegationDelegators = new AionMap<>();
    }
}
