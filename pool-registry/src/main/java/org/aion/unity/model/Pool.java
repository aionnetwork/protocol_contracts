package org.aion.unity.model;

import avm.Address;
import org.aion.avm.userlib.AionMap;

import java.math.BigInteger;
import java.util.Map;

/**
 * Represents a registered pool.
 */
public class Pool {

    private Address ownerAddress;
    private Address signingAddress;
    private Address coinbaseAddress;
    private String name;
    private String description;
    private String website;
    private int commissionRate;

    private Map<Address, BigInteger> delegators = new AionMap<>();

    /**
     * Construct a pool object.
     *
     * @param ownerAddress    the owner address of the staker
     * @param signingAddress  the signing address of the staker
     * @param coinbaseAddress the coinbase address of the staker
     * @param name            the name of this pool
     * @param description     a description about this pool
     * @param website         the website URL of this pool
     * @param commissionRate  the commission rate in percentage
     */
    public Pool(Address ownerAddress, Address signingAddress, Address coinbaseAddress, String name, String description, String website, int commissionRate) {
        this.ownerAddress = ownerAddress;
        this.signingAddress = signingAddress;
        this.coinbaseAddress = coinbaseAddress;
        this.name = name;
        this.description = description;
        this.website = website;
        this.commissionRate = commissionRate;
    }

    /**
     * Returns the direct reference to the associated delegators.
     *
     * @return a map of delegators, from address to vote, mutable.
     */
    private Map<Address, BigInteger> getDelegators() {
        return delegators;
    }

    public Address getOwnerAddress() {
        return ownerAddress;
    }

    public void setOwnerAddress(Address ownerAddress) {
        this.ownerAddress = ownerAddress;
    }

    public Address getSigningAddress() {
        return signingAddress;
    }

    public void setSigningAddress(Address signingAddress) {
        this.signingAddress = signingAddress;
    }

    public Address getCoinbaseAddress() {
        return coinbaseAddress;
    }

    public void setCoinbaseAddress(Address coinbaseAddress) {
        this.coinbaseAddress = coinbaseAddress;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public int getCommissionRate() {
        return commissionRate;
    }

    public void setCommissionRate(int commissionRate) {
        this.commissionRate = commissionRate;
    }
}
