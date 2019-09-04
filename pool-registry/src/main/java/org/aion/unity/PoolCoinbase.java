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
public class PoolCoinbase {

    @Initializable
    private static Address poolRegistry;

    @Callable
    public static void transfer(Address recipient, BigInteger amount) {
        // only the pool registry
        Blockchain.require(Blockchain.getCaller().equals(poolRegistry));

        // sanity check
        Blockchain.require(recipient != null);
        // amount > 0
        Blockchain.require(amount.signum() == 1);

        // transfer
        Result result = Blockchain.call(recipient, amount, new byte[0], Blockchain.getRemainingEnergy());
        Blockchain.require(result.isSuccess());
    }
}
