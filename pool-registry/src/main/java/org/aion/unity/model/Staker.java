package org.aion.unity.model;

import avm.Address;

public class Staker {

    private Address ownerAddress;
    private Address signingAddress;
    private Address coinbaseAddress;

    public Staker(Address ownerAddress, Address signingAddress, Address coinbaseAddress) {
        this.ownerAddress = ownerAddress;
        this.signingAddress = signingAddress;
        this.coinbaseAddress = coinbaseAddress;
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
}
