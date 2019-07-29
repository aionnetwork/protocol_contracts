package org.aion.unity;

import avm.Address;

import java.math.BigInteger;

/**
 * A staker registry manages the registration of stakers, and provides an interface for coin-holders
 * to vote/unvote for a staker.
 */
public interface StakerRegistry {

    // TODO: use BigInteger for stake

    /**
     * Registers a staker. The caller address will be the identification
     * address of a registered staker.
     *
     * @param signingAddress  the address of key used for signing a PoS block
     * @param coinbaseAddress the address of key used for collecting block rewards
     * @return true if successful, otherwise false
     */
    boolean registerStaker(Address signingAddress, Address coinbaseAddress);

    /**
     * Votes for a staker. Any liquid coins, passed along the call, become locked stake.
     *
     * @param staker the address of the staker
     * @return true if successful, otherwise false
     */
    boolean vote(Address staker);

    /**
     * Unvotes for a staker. After a successful unvote, the locked coins will be released
     * to the original owners, subject to lock-up period.
     *
     * @param staker the address of the staker
     * @param amount the amount of stake
     * @return true if successful, otherwise false
     */
    boolean unvote(Address staker, long amount);

    /**
     * Un-votes for a staker, and receives the released fund using another account.
     *
     * @param staker   the address of the staker
     * @param amount   the amount of stake
     * @param receiver the receiving addresss
     * @return true if successful, otherwise false
     */
    boolean unvoteTo(Address staker, long amount, Address receiver);


    /**
     * Transfers stake from one staker to another staker.
     *
     * @param fromStaker the address of the staker to transfer stake from
     * @param toStaker   the address of the staker to transfer stake to
     * @param amount     the amount of stake
     * @return true if successful, otherwise false
     */
    boolean transferStake(Address fromStaker, Address toStaker, long amount);

    /**
     * Returns the total stake associated with a staker.
     *
     * @param staker the address of the staker
     * @return the total amount of stake
     */
    long getTotalStake(Address staker);

    /**
     * Returns the stake from a voter to a staker.
     *
     * @param staker the address of the staker
     * @param voter  the address of the staker
     * @return the amount of stake
     */
    long getStake(Address staker, Address voter);

    /**
     * Returns the signing address of a staker.
     *
     * @param staker the address of the staker.
     * @return the signing address
     */
    Address getSigningAddress(Address staker);

    /**
     * Returns the coinbase address of a staker.
     *
     * @param staker the address of the staker.
     * @return the coinbase address
     */
    Address getCoinbaseAddress(Address staker);

    /**
     * Updates the signing address of a staker. Owner only.
     *
     * @param staker            the address of the staker
     * @param newSigningAddress the new signing address
     */
    void setSigningAddress(Address staker, Address newSigningAddress);

    /**
     * Updates the coinbase address of a staker. Owner only.
     *
     * @param staker             the address of the staker
     * @param newCoinbaseAddress the new coinbase address
     */
    void setCoinbaseAddress(Address staker, Address newCoinbaseAddress);

    /**
     * Registers a listener. Owner only.
     *
     * @param listener the address of the listener contract
     */
    void registerListener(Address listener);

    /**
     * Deregisters a listener. Owner only.
     *
     * @param listener the address of the listener contract
     */
    void deregisterListener(Address listener);
}
