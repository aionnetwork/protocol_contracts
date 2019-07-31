package org.aion.unity;

import avm.Address;
import org.aion.avm.userlib.AionMap;

import java.math.BigInteger;
import java.util.Map;

/**
 * Manages the state of a pool.
 */
public class PoolState {
    boolean initialized;

    Address stakerAddress;
    Address coinbaseAddress;

    byte[] metaData;
    int commissionRate;

    Map<Address, BigInteger> delegators;

    public PoolState(Address stakerAddress, Address coinbaseAddress, byte[] metaData, int commissionRate) {
        this.stakerAddress = stakerAddress;
        this.coinbaseAddress = coinbaseAddress;
        this.metaData = metaData;
        this.commissionRate = commissionRate;
        this.delegators = new AionMap<>();
    }

    // TODO: implement rewards management
}
