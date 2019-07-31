package org.aion.unity;

import avm.Address;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.tooling.abi.Initializable;
import org.aion.avm.userlib.AionMap;

import java.util.Map;

/**
 * A stake delegation registry manages a list of registered pools, votes/un-votes on delegator's behalf, and
 * ensure the delegators receive its portion of the block rewards.
 */
public class PoolRegistry {

    private static Map<Address, PoolState> pools = new AionMap<>();

    @Initializable
    private static Address stakerRegistry;

    @Callable
    public static Address getStakerRegistry() {
        return stakerRegistry;
    }


    /**
     * Register a pool in the registry.
     *
     * @param metaData       the pool meta data
     * @param commissionRate the pool commission rate
     * @return the pool coinbase address
     */
    @Callable
    public static Address registerPool(byte[] metaData, int commissionRate) {

        return null;
    }

    /**
     * Delegates stake to a pool.
     *
     * @param pool the pool address
     */
    @Callable
    public static void delegate(Address pool) {

    }

    /**
     * Cancels stake to a pool.
     *
     * @param pool   the pool address
     * @param amount the amount of stake to undelegate
     */
    @Callable
    public static void undelegate(Address pool, long amount) {

    }

    /**
     * Redelegate a pool using the rewards.
     *
     * @param pool the pool address
     */
    @Callable
    public static void redelegate(Address pool) {

    }

    /**
     * Transfers stake from one pool to another.
     *
     * @param fromPool the from pool address
     * @param toPool   the to pool address
     * @param amount   the amount of stake to transfer
     */
    @Callable
    public static void transferStake(Address fromPool, Address toPool, long amount) {

    }

    /**
     * Withdraws rewards from one pool
     *
     * @param pool  the pool address
     * @param limit the withdraw limit
     */
    @Callable
    public static void withdraw(Address pool, long limit) {

    }

    @Callable
    public static void onSigningAddressChange(Address staker, Address newSigningAddress) {

    }

    @Callable
    public static void onCoinbaseAddressChange(Address staker, Address newCoinbaseAddress) {

    }

    @Callable
    public static void onListenerAdded(Address staker, Address listener) {

    }

    @Callable
    public static void onListenerRemoved(Address staker, Address listener) {

    }
}
