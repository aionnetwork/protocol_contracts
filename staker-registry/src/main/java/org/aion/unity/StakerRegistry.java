package org.aion.unity;

import avm.Address;

import java.math.BigInteger;

/**
 * A collection of functions supported by the staking registry contract.
 */
public interface StakerRegistry {

    /**
     * Returns the stake information.
     *
     * <ul>
     * <li>Owner/staker address - address of the staker owner, used to identify a staker, immutable</li>
     * <li>Signing address - address of the signing key</li>
     * <li>Coinbase address - address for collecting block rewards</li>
     * </ul>
     * <p>
     * Note: ideally, the return value should be an object; using an array as custom data
     * structure is not yet supported by ABI.
     *
     * @param staker the address of the staker
     * @return a data structure, [owner address, signing address, coinbase address].
     */
    Address[] getStakerAddresses(Address staker);

    /**
     * Votes for a staker.
     *
     * @param staker the address of the staker to vote
     */
    void vote(Address staker);


    /**
     * Un-votes for a staker.
     *
     * @param staker the address of the staker
     * @param amount the amount of stake
     */
    void unvote(Address staker, BigInteger amount);


    /**
     * Un-votes for a staker, and release the funds to another address.
     *
     * @param staker   the address of the staker
     * @param amount   the amount of stake
     * @param receiver
     */
    void unvoteTo(Address staker, BigInteger amount, Address receiver);
}
