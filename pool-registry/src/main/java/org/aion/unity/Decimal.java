package org.aion.unity;

import java.math.BigInteger;

@SuppressWarnings({"unused", "WeakerAccess"})
public class Decimal {

    // class settings
    // ==============
    private final static int precision = 18;
    // bytes required to represent the above precision
    // Ceiling[Log2[999 999 999 999 999 999]]
    private final static int DecimalPrecisionBits = 60;

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

        if (r.bitLength() > 255 + DecimalPrecisionBits)
            throw new RuntimeException("Int overflow");

        return new Decimal(r);
    }

    // subtraction
    public Decimal subtract(Decimal d) {
        BigInteger r = value.subtract(d.value);

        if (r.bitLength() > 255 + DecimalPrecisionBits)
            throw new RuntimeException("Int overflow");

        return new Decimal(r);
    }

    // multiplication truncate
    public Decimal multiplyTruncate(Decimal d) {
        // multiply precision twice
        BigInteger mul = value.multiply(d.value);
        BigInteger chopped = chopPrecisionAndTruncate(mul);

        if (chopped.bitLength() > 255 + DecimalPrecisionBits)
            throw new RuntimeException("Int overflow");

        return new Decimal(chopped);
    }

    // multiplication truncate
    public Decimal divideTruncate(Decimal d) {
        // multiply precision twice
        BigInteger mul = value.multiply(precisionInt).multiply(precisionInt);
        BigInteger quo = mul.divide(d.value);
        BigInteger chopped = chopPrecisionAndTruncate(quo);

        if (chopped.bitLength() > 255 + DecimalPrecisionBits)
            throw new RuntimeException("Int overflow");

        return new Decimal(chopped);
    }

    private BigInteger chopPrecisionAndTruncate(BigInteger d) {
        return d.divide(precisionInt);
    }
}
