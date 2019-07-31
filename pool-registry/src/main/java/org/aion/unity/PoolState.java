package org.aion.unity;

import avm.Address;
import org.aion.avm.userlib.AionMap;

import java.math.BigInteger;
import java.util.Map;

/**
 * Manages the state of a pool.
 */
public class PoolState {
    private Address stakerAddress;
    private Address rewardsAddress;

    private byte[] metaData;
    private int commissionRate;

    private Map<Address, BigInteger> delegators;

    public PoolState(Address stakerAddress, Address rewardsAddress, byte[] metaData, int commissionRate) {
        this.stakerAddress = stakerAddress;
        this.rewardsAddress = rewardsAddress;
        this.metaData = metaData;
        this.commissionRate = commissionRate;
        this.delegators = new AionMap<>();
    }

    // TODO: implement rewards management
}
