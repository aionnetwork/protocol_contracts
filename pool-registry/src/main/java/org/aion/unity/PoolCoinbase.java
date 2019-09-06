package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import avm.Result;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.tooling.abi.Fallback;
import org.aion.avm.tooling.abi.Initializable;

import java.math.BigInteger;

/**
 * A dummy contract to collect block rewards.
 */
public class PoolCoinbase {

    @Initializable
    private static Address poolRegistry;

    @Callable
    public static void transfer(BigInteger amount) {
        // only the pool registry
        Blockchain.require(Blockchain.getCaller().equals(poolRegistry));

        // amount > 0
        Blockchain.require(amount.signum() == 1);

        // transfer
        Result result = Blockchain.call(poolRegistry, amount, new byte[0], Blockchain.getRemainingEnergy());
        Blockchain.require(result.isSuccess());
    }

    // transferring value (block rewards) to the coinbase address is done internally through the kernel.
    // this is different than a normal transaction, thus the fallback method is used to reject any other value transfer.
    @Fallback
    public static void fallback(){
        Blockchain.revert();
    }
}
