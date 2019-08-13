package org.aion.unity;

import java.math.BigInteger;

import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.tooling.abi.Initializable;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;

import avm.Address;
import avm.Blockchain;
import avm.Result;

/**
 * A contract designated for holding the stake from pool owners.
 */
public class PoolSlashableStake {

    // TODO: replace long with BigInteger

    @Initializable
    private static Address poolRegistry;

    @Initializable
    private static Address stakerRegistry;

    @Callable
    public static void transfer(Address recipient, long amount) {
        requirePoolRegistry();

        // sanity check
        require(recipient != null);
        require(amount > 0);

        // transfer
        secureCall(recipient, BigInteger.valueOf(amount), new byte[0], Blockchain.getRemainingEnergy());
    }

    @Callable
    public static void vote(Address staker, long amount) {
        requirePoolRegistry();

        // sanity check
        require(staker != null);
        require(amount > 0);

        // vote
        byte[] data = new ABIStreamingEncoder()
                .encodeOneString("vote")
                .encodeOneAddress(staker)
                .toBytes();
        secureCall(stakerRegistry, BigInteger.valueOf(amount), data, Blockchain.getRemainingEnergy());
    }

    @Callable
    public static long unvote(Address staker, long amount) {
        requirePoolRegistry();

        // sanity check
        require(staker != null);
        require(amount > 0);

        // unvote
        byte[] data = new ABIStreamingEncoder()
                .encodeOneString("vote")
                .encodeOneAddress(staker)
                .encodeOneLong(amount)
                .toBytes();
        Result result = secureCall(stakerRegistry, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
        return new ABIDecoder(result.getReturnData()).decodeOneLong();
    }

    @Callable
    public static long unvoteTo(Address staker, long amount, Address recipient) {
        requirePoolRegistry();

        // sanity check
        require(staker != null);
        require(amount > 0);
        require(recipient != null);

        // unvote to
        byte[] data = new ABIStreamingEncoder()
                .encodeOneString("unvoteTo")
                .encodeOneAddress(staker)
                .encodeOneLong(amount)
                .encodeOneAddress(recipient)
                .toBytes();
        Result result = secureCall(stakerRegistry, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
        return new ABIDecoder(result.getReturnData()).decodeOneLong();
    }

    @Callable
    public static long transferStake(Address from, Address to, long amount) {
        requirePoolRegistry();

        // sanity check
        require(from != null);
        require(to != null);
        require(amount > 0);

        // transfer staker
        byte[] data = new ABIStreamingEncoder()
                .encodeOneString("transferStake")
                .encodeOneAddress(from)
                .encodeOneAddress(to)
                .encodeOneLong(amount)
                .toBytes();
        Result result = secureCall(stakerRegistry, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
        return new ABIDecoder(result.getReturnData()).decodeOneLong();
    }

    private static void require(boolean condition) {
        Blockchain.require(condition);
    }

    private static void requirePoolRegistry() {
        require(Blockchain.getCaller().equals(poolRegistry));
    }

    private static Result secureCall(Address targetAddress, BigInteger value, byte[] data, long energyLimit) {
        Result result = Blockchain.call(targetAddress, value, data, energyLimit);
        require(result.isSuccess());
        return result;
    }
}
