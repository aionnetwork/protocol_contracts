package org.aion.unity.resources;

import avm.Address;
import avm.Blockchain;
import avm.Result;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.tooling.abi.Fallback;
import org.aion.avm.tooling.abi.Initializable;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;

import java.math.BigInteger;

public class ReentrantContract {

    @Initializable
    public static Address poolRegistry;
    @Initializable
    public static Address pool;

    private static boolean hasBeenCalled = false;
    private static byte[] txData;

    @Callable
    public static void delegate() {
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(pool)
                .toBytes();

        Result result = Blockchain.call(poolRegistry, Blockchain.getValue(), txData, Blockchain.getRemainingEnergy());
        Blockchain.require(result.isSuccess());
    }

    @Callable
    public static void autoDelegateRewards() {
        byte[] data = new ABIStreamingEncoder()
                .encodeOneString("autoDelegateRewards")
                .encodeOneAddress(pool)
                .encodeOneAddress(Blockchain.getAddress())
                .toBytes();
        txData = data;
        makeCall();
    }

    @Callable
    public static void enableAutoRewardsDelegation() {
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("enableAutoRewardsDelegation")
                .encodeOneAddress(pool)
                .encodeOneInteger(1)
                .toBytes();
        Result result = Blockchain.call(poolRegistry, BigInteger.ZERO, txData, Blockchain.getRemainingEnergy());
        Blockchain.require(result.isSuccess());
    }

    @Callable
    public static void withdrawRewards(){
        byte[] data = new ABIStreamingEncoder()
                .encodeOneString("withdrawRewards")
                .encodeOneAddress(pool)
                .toBytes();
        txData = data;
        makeCall();
    }

    @Fallback
    public static void fallback() {
        // call back to the pool registry, if this is the first call from PoolRegistry to this contract
        if (!hasBeenCalled) {
            hasBeenCalled = true;
            makeCall();
        }
    }

    private static void makeCall() {
        Result result = Blockchain.call(poolRegistry, BigInteger.ZERO, txData, Blockchain.getRemainingEnergy());
        Blockchain.require(result.isSuccess());
    }
}
