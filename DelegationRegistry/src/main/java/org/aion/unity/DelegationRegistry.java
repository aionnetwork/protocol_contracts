package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.tooling.abi.Initializable;
import org.aion.avm.userlib.AionMap;
import org.aion.unity.model.Pool;

import java.math.BigInteger;
import java.util.Map;


/**
 * A stake delegation registry manages a list of registered pools, votes/un-votes on delegator's behalf, and
 * ensure the delegators receive the desired share of block rewards.
 * <p>
 * Terminologies: (Ali's definition)
 * <ul>
 * <ul>stake - The locked coins</ul>
 * <ul>staker -  A PoS block producer registered on the stake registry contract</ul>
 * <ul>pool - A staker that accepts stake contribution from other coin holders</ul>
 * <ul>delegator - A coin-holder who contributes to a pool</ul>
 * </ul>
 * <p>
 * Operations: (Production team's definition)
 * <ul>
 * <ul>vote - Casts liquid coins into stake for a staker</ul>
 * <ul>unvote - Reverts a vote</ul>
 * </ul>
 * <p>
 * Challenges that we've identified:
 * <ul>
 * <li>How to ensure the block rewards go to this contract?</li>
 * <li>How to accumulate/compute the rewards for each delegator? the complexity is linear to the number of delegators
 * , which leads to variant energy cost</li>
 * <li>How does the kernel notify the stake delegation contract about a block being generated?</li>
 * </ul>
 * <p>
 * We've making the following assumptions about the stake registry contract:
 * <ul>
 * <li>The self-bond requirement is enforced;</li>
 * <li>Signing key and coinbasse key swap is allowed;</li>
 * <li>Signing key and coinbase key are validated during PoS block validation;</li>
 * <li>Stake contract events are passed to this contract.</li>
 * </ul>
 * <p>
 * NOTE: also, we will start with the assumption that all computations is free!!!!
 */
public class DelegationRegistry implements StakeRegistryListener {

    /**
     * The address of the stake registry contract.
     */
    @Initializable
    private static Address stakeRegistry;


    /**
     * The registered pool, indexed by the owner address.
     */
    private static Map<Address, Pool> pools = new AionMap<>();

    @Callable
    public static String getNames() {
        return "Stake Delegation Registry";
    }

    @Callable
    public static Address getStakeRegistry() {
        return stakeRegistry;
    }

    /**
     * Registers a staking pool.
     */
    @Callable
    public static boolean register(Address ownerAddress, Address signingAddress, Address coinbaseAddress, String name, String description, String website, int commissionRate) {
        // Only the owner can register a pool
        if (!Blockchain.getCaller().equals(ownerAddress)) {
            return false;
        }

        // Input arguments sanity check (no justification has been made)
        if (signingAddress == null
                || coinbaseAddress == null
                || name == null || name.length() > 128
                || description == null || description.length() > 1024
                || website == null || website.length() > 256
                || commissionRate < 0 || commissionRate > 100) {
            return false;
        }

        // Check if the pool has already been registered.
        if (pools.containsKey(ownerAddress)) {
            return false;
        }

        // TODO: check if the staker record is accurate, via a CALL to the stake registry contract.

        // The coinbase address has to be this contract
        if (!coinbaseAddress.equals(Blockchain.getAddress())) {
            return false;
        }

        // Construct a pool instance and add it to the registry
        pools.put(ownerAddress, new Pool(ownerAddress, signingAddress, coinbaseAddress, name, description, website, commissionRate));

        return true;
    }

    /**
     * Returns a list of registered pools, in JSON format.
     */
    @Callable
    public static String list() {
        return null;
    }

    /**
     * Votes for a pool
     *
     * @param pool the address of the pool to vote
     * @return true if this operation was successful, otherwise false.
     */
    @Callable
    public static boolean vote(Address pool) {
        return false;
    }

    /**
     * Un-votes for a pool.
     *
     * @param pool   the address of the pool to vote.
     * @param amount the amount to unvote.
     * @return true if this operation was successful, otherwise false.
     */
    @Callable
    public static boolean unvote(Address pool, long amount) {
        return false;
    }

    /**
     * Claims block rewards.
     *
     * @param limit the max amount of rewards to claim.
     * @return true if this operation was successful, otherwise false.
     */
    @Callable
    public static boolean claimRewards(long limit) {
        return false;
    }

    @Override
    public void onStakerCoinbaseKeyChange(Address staker, Address newCoinbaseAddress) {

    }

    @Override
    public void onStakerSigningKeyChange(Address staker, Address newSigningAddress) {

    }

    @Override
    public void onBlockProduced(byte[] staker, byte[] hash, BigInteger rewards) {

    }
}
