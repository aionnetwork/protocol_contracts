package org.aion.unity;

import java.math.BigInteger;

/**
 * Notes on Precision Selection
 * ----------------------------
 * <p>
 * WLOG, all amounts are denominated in some units of "coin".
 * <p>
 * When we refer to "precision", we mean the number of precision places required to represent the smallest possible real
 * number that can arise when we divide the smallest unit of staked coin, with the largest size of the pool.
 * <p>
 * Such quantity required in the computation of the F1 rewards distribution scheme.
 * <p>
 * Consider the following degenerate case: all coins in the system are delegated to one pool, and a delegator has
 * staked 1 coin to this pool. In this case, the maximum precision "precision" we need is the number of precision places
 * required to represent (max # of coins in the system)^-1
 * <p>
 * Now, if there is an upper limit to the number of coins that can be delegated to a pool, then the "precision" we
 * need would be the number of precision places required to represent (max # of coins per pool)^-1. In the
 * absence of such a maximum, in the design of this system, we fall-back to the "precision" that depends on the max #
 * of coins in the system.
 * <p>
 * Furthermore, we run into another problem here; the max # of coins in the system has not been defined for Aion. In
 * order to resolve this, we consider the maximum number which can be represented by a 64-bit signed (Java) long,
 * the number of precision places required is ceil(log_10(2^63 - 1)) = ceil(18.96) = 19 ~ 20.
 * <p>
 * Assuming the AVM data-word limit of 128-bit unsigned integer, the number of precision places required
 * is ceil(log_10(2^128 - 1)) = ceil(38.53) = 39 ~ 40.
 * <p>
 * We pick 18 precision places as the "precision" in this proof of concept, since it fits inside a Long.
 * <p>
 * Under such a precision regime:
 * > All additions and subtractions can be computed without precision loss
 * > Multiplications would need to be performed with double the precision (40 precision places), which is then
 * truncated down to 20 precision places.
 * <p>
 * We've used long to represent all "coin" units in this system. A decision needs to be made about the units used
 * in the smart contract; if we're using base units (nAmp) or Aion, or some other quanta of coin.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class Decimal {

    // class settings
    // ==============
    private final static int precision = 18;
    /* TODO: The value of precisionInt should be tied to the value of precision (defined above) directly; initially
        this was implemented like so: BigInteger.TEN.pow(precision). We need a better was of initializing this
        BigInteger (maybe using a String instead of a Long), since more than 20 decimals will overflow Long. */
    private static BigInteger precisionInt = BigInteger.valueOf(1_000_000_000_000_000_000L);
    private final BigInteger value;

    private Decimal(BigInteger v) {
        assert (v != null);
        assert (v.signum() == 1 || v.signum() == 0); // coin must be either positive or 0

        this.value = v;
    }

    public static Decimal valueOf(long v) {
        // important to do the precision expansion here!
        return new Decimal(BigInteger.valueOf(v).multiply(precisionInt));
    }

    public BigInteger getTruncated() {
        return chopPrecisionAndTruncate(value);
    }

    // common values
    public static Decimal ZERO = valueOf(0);
    public static Decimal ONE = valueOf(1);
    public static Decimal SMALLEST_DECIMAL = valueOf(1);

    // utility functions
    public boolean equals(Decimal d) {
        return value.compareTo(d.value) == 0;
    }

    public boolean greaterThan(Decimal d) {
        return value.compareTo(d.value) > 0;
    }

    public boolean greaterThanOrEqualTo(Decimal d) {
        return value.compareTo(d.value) >= 0;
    }

    public boolean lessThan(Decimal d) {
        return value.compareTo(d.value) < 0;
    }

    public boolean lessThanOrEqualTo(Decimal d) {
        return value.compareTo(d.value) <= 0;
    }

    // addition
    public Decimal add(Decimal d) {
        BigInteger r = value.add(d.value);

        return new Decimal(r);
    }

    // subtraction
    public Decimal subtract(Decimal d) {
        BigInteger r = value.subtract(d.value);

        return new Decimal(r);
    }

    // multiplication truncate
    public Decimal multiplyTruncate(Decimal d) {
        // multiply precision twice
        BigInteger mul = value.multiply(d.value);
        BigInteger chopped = chopPrecisionAndTruncate(mul);

        return new Decimal(chopped);
    }

    // multiplication truncate
    public Decimal divideTruncate(Decimal d) {
        // multiply precision twice
        BigInteger mul = value.multiply(precisionInt).multiply(precisionInt);
        BigInteger quo = mul.divide(d.value);
        BigInteger chopped = chopPrecisionAndTruncate(quo);

        return new Decimal(chopped);
    }

    private BigInteger chopPrecisionAndTruncate(BigInteger d) {
        return d.divide(precisionInt);
    }

    // TODO: is the truncated version is appropriate? (or if full expansion (without truncation) should be used)
    @Override
    public String toString() {
        return getTruncated().toString();
    }
}
