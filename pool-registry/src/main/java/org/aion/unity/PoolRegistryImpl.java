package org.aion.unity;

import avm.Address;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.tooling.abi.Initializable;
import org.aion.avm.userlib.AionMap;

import java.math.BigInteger;
import java.util.Map;


public class PoolRegistryImpl {

    private static Map<Address, Pool> pools = new AionMap<>();

    @Initializable
    private static Address stakerRegistry;

    @Callable
    public static Address getStakerRegistry() {
        return stakerRegistry;
    }


    @Callable
    public static boolean registerPool(byte[] metaData, int commissionRate) {
        return false;
    }

    @Callable
    public static void onSigningAddressChange(Address staker, Address newSigningAddress) {

    }

    @Callable
    public static void onCoinbaseAddressChange(Address staker, Address newCoinbaseAddress) {

    }

    @Callable
    public static void onBlockProduction(Address staker, long number, byte[] hash, long rewards) {

    }

    public static class Pool {

        private Address ownerAddress;
        private Address signingAddress;
        private Address coinbaseAddress;
        private byte[] metaData;
        private int commissionRate;

        private Map<Address, BigInteger> delegators;

        public Pool(Address ownerAddress, Address signingAddress, Address coinbaseAddress, byte[] metaData, int commissionRate) {
            this.ownerAddress = ownerAddress;
            this.signingAddress = signingAddress;
            this.coinbaseAddress = coinbaseAddress;
            this.metaData = metaData;
            this.commissionRate = commissionRate;
            this.delegators = new AionMap<>();
        }
    }
}
