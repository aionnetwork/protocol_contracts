package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import avm.Result;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.tooling.abi.Initializable;

import java.math.BigInteger;

/**
 * A dummy contract to collect block rewards.
 */
public class PoolCoinbasee {

    // TODO: replace long with BigInteger

    @Initializable
    private static Address poolRegistry;

    @Callable
    public static void transfer(Address recipient, long amount) {
        // only the pool registry
        Blockchain.require(Blockchain.getCaller().equals(poolRegistry));

        // sanity check
        Blockchain.require(recipient != null);
        Blockchain.require(amount > 0);

        // transfer
        Result result = Blockchain.call(recipient, BigInteger.valueOf(amount), new byte[0], Blockchain.getRemainingEnergy());
        Blockchain.require(result.isSuccess());
    }
}
