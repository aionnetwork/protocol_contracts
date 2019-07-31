package org.aion.unity;

import avm.Address;

import java.math.BigInteger;

public interface StakerRegistryListener {

    /**
     * When the signing address of a staker is changed.
     *
     * @param staker            the staker address
     * @param newSigningAddress the new signing address
     */
    void onSigningAddressChange(Address staker, Address newSigningAddress);

    /**
     * When the coinbase address of a staker is changed.
     *
     * @param staker             the staker address
     * @param newCoinbaseAddress the new coinbase address
     */
    void onCoinbaseAddressChange(Address staker, Address newCoinbaseAddress);

    void onListenerAdded(Address staker, Address listener);

    void onListenerRemoved(Address staker, Address listener);
}
