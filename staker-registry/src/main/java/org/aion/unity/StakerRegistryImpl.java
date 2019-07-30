package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import avm.Result;

import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.userlib.AionMap;
import org.aion.avm.userlib.AionSet;
import org.aion.avm.userlib.abi.ABIEncoder;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;


public class StakerRegistryImpl {

    // TODO: replace long with BigInteger once the ABI supports it.

    // Conventions:
    // 1. REQUIRE exception is thrown when the input data is malformed or the staker doesn't exist
    // 2. The return value is to indicate the operation result

    private static class Staker {
        private Address signingAddress;
        private Address coinbaseAddress;
        private BigInteger totalStake;

        // maps addresses to the stakes those addresses have sent to this staker
        // the sum of stakes.values() should always equal totalStake
        private Map<Address, BigInteger> stakes;

        private Set<Address> listeners;

        public Staker(Address signingAddress, Address coinbaseAddress) {
            this.signingAddress = signingAddress;
            this.coinbaseAddress = coinbaseAddress;
            this.totalStake = BigInteger.ZERO;
            this.stakes = new AionMap<>();
            this.listeners = new AionSet<>();
        }
    }

    private static Map<Address, Staker> stakers = new AionMap<>();

    @Callable
    public static boolean registerStaker(Address signingAddress, Address coinbaseAddress) {
        Address caller = Blockchain.getCaller();

        requireNonNull(signingAddress);
        requireNonNull(coinbaseAddress);

        if (!stakers.containsKey(caller)) {
            stakers.put(caller, new Staker(signingAddress, coinbaseAddress));
            return true;
        }

        return false;
    }

    @Callable
    public static boolean vote(Address staker) {
        Address caller = Blockchain.getCaller();
        BigInteger amount = Blockchain.getValue();

        requireStaker(staker);
        requirePositive(amount);

        Staker s = stakers.get(staker);
        s.totalStake = s.totalStake.add(amount);
        BigInteger previousStake = getOrDefault(s.stakes, caller, BigInteger.ZERO);
        putOrRemove(s.stakes, caller, previousStake.add(amount));

        return true;
    }

    @Callable
    public static boolean unvote(Address staker, long amount) {
        return unvoteTo(staker, amount, Blockchain.getCaller());
    }

    @Callable
    public static boolean unvoteTo(Address staker, long amount, Address receiver) {
        Address caller = Blockchain.getCaller();

        requireStaker(staker);
        requirePositive(amount);
        requireNonNull(receiver);

        Staker s = stakers.get(staker);
        BigInteger previousStake = getOrDefault(stakers.get(staker).stakes, caller, BigInteger.ZERO);
        BigInteger amountBI = BigInteger.valueOf(amount);

        if (amountBI.compareTo(previousStake) <= 0) {
            s.totalStake = s.totalStake.subtract(amountBI);
            putOrRemove(s.stakes, caller, previousStake.subtract(amountBI));

            Blockchain.call(receiver, amountBI, new byte[0], Blockchain.getRemainingEnergy());
            return true;
        }

        return false;
    }

    @Callable
    public static boolean transferStake(Address fromStaker, Address toStaker, long amount) {
        Address caller = Blockchain.getCaller();

        requireStaker(fromStaker);
        requireStaker(toStaker);
        requirePositive(amount);

        Staker s1 = stakers.get(fromStaker);
        Staker s2 = stakers.get(toStaker);
        BigInteger previousStake1 = getOrDefault(s1.stakes, caller, BigInteger.ZERO);
        BigInteger previousStake2 = getOrDefault(s2.stakes, caller, BigInteger.ZERO);
        BigInteger amountBI = BigInteger.valueOf(amount);

        if (amountBI.compareTo(previousStake1) <= 0) {
            s1.totalStake = s1.totalStake.subtract(amountBI);
            putOrRemove(s1.stakes, caller, previousStake1.subtract(amountBI));

            s2.totalStake = s2.totalStake.add(amountBI);
            putOrRemove(s2.stakes, caller, previousStake2.add(amountBI));

            return true;
        }

        return false;
    }

    @Callable
    public static long getTotalStake(Address staker) {
        requireStaker(staker);

        return stakers.get(staker).totalStake.longValue();
    }

    @Callable
    public static long getStake(Address staker, Address voter) {
        requireStaker(staker);

        return getOrDefault(stakers.get(staker).stakes, voter, BigInteger.ZERO).longValue();
    }

    @Callable
    public static Address getSigningAddress(Address staker) {
        requireStaker(staker);

        return stakers.get(staker).signingAddress;
    }

    @Callable
    public static Address getCoinbaseAddress(Address staker) {
        requireStaker(staker);

        return stakers.get(staker).coinbaseAddress;
    }

    @Callable
    public static void setSigningAddress(Address newSigningAddress) {
        Address staker = Blockchain.getCaller();
        requireStaker(staker);

        Staker s = stakers.get(staker);
        s.signingAddress = newSigningAddress;

        for (Address listener : s.listeners) {
            byte[] data =  new ABIStreamingEncoder()
                    .encodeOneString("onSigningAddressChange")
                    .encodeOneAddress(staker)
                    .encodeOneAddress(newSigningAddress)
                    .toBytes();
            Blockchain.call(listener, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
        }
    }

    @Callable
    public static void setCoinbaseAddress(Address newCoinbaseAddress) {
        Address staker = Blockchain.getCaller();
        requireStaker(staker);

        Staker s = stakers.get(staker);
        s.coinbaseAddress = newCoinbaseAddress;

        for (Address listener : s.listeners) {
            byte[] data =  new ABIStreamingEncoder()
                    .encodeOneString("onCoinbaseAddressChange")
                    .encodeOneAddress(staker)
                    .encodeOneAddress(newCoinbaseAddress)
                    .toBytes();
            Blockchain.call(listener, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
        }
    }

    @Callable
    public static void registerListener(Address listener) {
        Address caller = Blockchain.getCaller();

        requireStaker(caller);
        stakers.get(caller).listeners.add(listener);
    }

    @Callable
    public static void deregisterListener(Address listener) {
        Address caller = Blockchain.getCaller();

        requireStaker(caller);
        stakers.get(caller).listeners.remove(listener);
    }

    private static void requireStaker(Address staker) {
        Blockchain.require(staker != null && stakers.containsKey(staker));
    }

    private static void requirePositive(BigInteger num) {
        Blockchain.require(num != null && num.compareTo(BigInteger.ZERO) > 0);
    }

    private static void requirePositive(long num) {
        Blockchain.require(num > 0);
    }

    private static void requireNonNull(Object obj) {
        Blockchain.require(obj != null);
    }

    private static <K, V extends BigInteger> void putOrRemove(Map<K, V> map, K key, V value) {
        if (value == null || value.compareTo(BigInteger.ZERO) == 0) {
            map.remove(key);
        } else {
            map.put(key, value);
        }
    }

    private static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultValue) {
        if (map.containsKey(key)) {
            return map.get(key);
        } else {
            return defaultValue;
        }
    }
}
