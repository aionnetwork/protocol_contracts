package org.aion.unity;

import avm.Address;

import org.aion.avm.core.util.ABIUtil;
import org.aion.avm.tooling.AvmRule;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;
import org.aion.vm.api.interfaces.ResultCode;
import org.junit.*;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.Scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PoolRegistryTest {

    private static BigInteger ENOUGH_BALANCE_TO_TRANSACT = BigInteger.TEN.pow(18 + 5);

    @Rule
    public AvmRule RULE = new AvmRule(true);

    // default address with balance
    private Address preminedAddress = RULE.getPreminedAccount();

    // contract address
    private Address stakerRegistry;
    private Address poolRegistry;

    private Address staker;

    @Before
    public void setup() {
        try (Scanner s = new Scanner(PoolRegistryTest.class.getResourceAsStream("StakerRegistry.txt"))) {
            String contract = s.nextLine();
            AvmRule.ResultWrapper result = RULE.deploy(preminedAddress, BigInteger.ZERO, Hex.decode(contract));
            assertTrue(result.getReceiptStatus().isSuccess());
            stakerRegistry = result.getDappAddress();
        }

        byte[] arguments = ABIUtil.encodeDeploymentArguments(stakerRegistry);
        byte[] data = RULE.getDappBytes(PoolRegistry.class, arguments, PoolState.class);
        poolRegistry = RULE.deploy(preminedAddress, BigInteger.ZERO, data).getDappAddress();

        // register a staker
        staker = RULE.getRandomAddress(ENOUGH_BALANCE_TO_TRANSACT);
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("registerStaker")
                .encodeOneAddress(staker)
                .encodeOneAddress(staker)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(staker, stakerRegistry, BigInteger.ZERO, txData);
        ResultCode status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());

        // register the staker as pool
        txData = ABIUtil.encodeMethodArguments("registerPool", new byte[0], 5);
        result = RULE.call(staker, poolRegistry, BigInteger.ZERO, txData);

        assertTrue(result.getReceiptStatus().isSuccess());
    }

    @Test
    public void testGetStakerRegistry() {
        byte[] txData = ABIUtil.encodeMethodArguments("getStakerRegistry");
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);

        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(stakerRegistry, result.getDecodedReturnData());
    }

    @Test
    public void testPoolCoinbaseContract() {
        byte[] arguments = ABIUtil.encodeDeploymentArguments(new Address(new byte[32]));
        byte[] data = RULE.getDappBytes(PoolCoinbasee.class, arguments);
        System.out.println(Hex.toHexString(data));
        System.out.println(data.length);
    }

    @Test
    public void testRegister() {
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("registerPool")
                .encodeOneByteArray("test".getBytes())
                .encodeOneInteger(5)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, poolRegistry, BigInteger.ZERO, txData);

        assertTrue(result.getReceiptStatus().isSuccess());
        assertTrue(result.getDecodedReturnData() instanceof Address);
    }

    @Test
    public void testDelegate() {
        BigInteger stake = BigInteger.TEN;

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(staker)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, poolRegistry, stake, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(staker)
                .encodeOneAddress(poolRegistry)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(stake.longValue(), result.getDecodedReturnData());
    }

    @Test
    public void testUndelegate() {
        BigInteger stake = BigInteger.TEN;
        BigInteger unstake = BigInteger.ONE;

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("delegate")
                .encodeOneAddress(staker)
                .toBytes();
        AvmRule.ResultWrapper result = RULE.call(preminedAddress, poolRegistry, stake, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("undelegate")
                .encodeOneAddress(staker)
                .encodeOneLong(unstake.longValue())
                .toBytes();
         result = RULE.call(preminedAddress, poolRegistry, stake, txData);
        assertTrue(result.getReceiptStatus().isSuccess());

        txData = new ABIStreamingEncoder()
                .encodeOneString("getStake")
                .encodeOneAddress(staker)
                .encodeOneAddress(poolRegistry)
                .toBytes();
        result = RULE.call(preminedAddress, stakerRegistry, BigInteger.ZERO, txData);
        assertTrue(result.getReceiptStatus().isSuccess());
        assertEquals(stake.longValue() - unstake.longValue(), result.getDecodedReturnData());
    }
}

