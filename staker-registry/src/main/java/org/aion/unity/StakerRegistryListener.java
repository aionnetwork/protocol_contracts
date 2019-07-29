package org.aion.unity;

import avm.Address;

import java.math.BigInteger;

public interface StakerRegistryListener {

    // TODO: use BigInteger for block rewards

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

    /**
     * When a PoS block is created.
     *
     * @param staker  the staker address
     * @param number  the block number
     * @param hash    the block hash
     * @param rewards the block rewards, including transaction fees collected
     */
    void onBlockProduction(Address staker, long number, byte[] hash, long rewards);
}
