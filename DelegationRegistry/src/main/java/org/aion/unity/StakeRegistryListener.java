package org.aion.unity;

import avm.Address;

import java.math.BigInteger;

public interface StakeRegistryListener {

    /**
     * When the coinbase address of a staker is changed.
     *
     * @param staker             the staker address
     * @param newCoinbaseAddress the new coinbase address
     */
    void onStakerCoinbaseKeyChange(Address staker, Address newCoinbaseAddress);

    /**
     * When the signing address of a staker is changed.
     *
     * @param staker            the staker address
     * @param newSigningAddress the new signing address
     */
    void onStakerSigningKeyChange(Address staker, Address newSigningAddress);

    /**
     * When a PoS block is created.
     *
     * @param staker  the staker address
     * @param hash    the block hash
     * @param rewards the block rewards, including transaction fees collected
     */
    void onBlockProduced(byte[] staker, byte[] hash, BigInteger rewards);
}
