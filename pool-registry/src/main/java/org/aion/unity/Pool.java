package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.tooling.abi.Initializable;

import java.math.BigInteger;

/**
 * A dummy contract to collect block rewards.
 */
public class Pool {

    // TODO: replace long with BigInteger

    @Initializable
    private static Address poolRegistry;

    @Callable
    public void withdraw(long limit) {
        Blockchain.require(limit > 0);
        Blockchain.require(Blockchain.getCaller().equals(poolRegistry));

        BigInteger toWithdraw = Blockchain.getBalance(Blockchain.getAddress());
        BigInteger limitBI = BigInteger.valueOf(limit);
        if (toWithdraw.compareTo(limitBI) > 0) {
            toWithdraw = limitBI;
        }

        Blockchain.call(poolRegistry, toWithdraw, new byte[0], Blockchain.getRemainingEnergy());
    }
}
