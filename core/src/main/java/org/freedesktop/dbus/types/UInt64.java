/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus.types;


import java.math.BigInteger;


/**
 * Class to represent unsigned 64-bit numbers.
 * Warning: Any functions which take or return a <tt>long</tt> are restricted to the range of a signed 64bit number.
 * Use the BigInteger methods if you wish access to the full
 * range.
 */
@SuppressWarnings ( "serial" )
public class UInt64 extends Number implements Comparable<UInt64> {

    /** Maximum allowed value (when accessed as a long) */
    public static final long MAX_LONG_VALUE = Long.MAX_VALUE;
    /** Maximum allowed value (when accessed as a BigInteger) */
    public static final BigInteger MAX_BIG_VALUE = new BigInteger("18446744073709551615");
    /** Minimum allowed value */
    public static final long MIN_VALUE = 0;
    private BigInteger value;
    private long top;
    private long bottom;


    /**
     * Create a UInt64 from a long.
     * 
     * @param value
     *            Must be a valid integer within MIN_VALUE&ndash;MAX_VALUE
     * @throws NumberFormatException
     *             if value is not between MIN_VALUE and MAX_VALUE
     */
    public UInt64 ( long value ) {
        if ( value < MIN_VALUE || value > MAX_LONG_VALUE )
            throw new NumberFormatException(String.format("%s is not between %s and %s.", value, MIN_VALUE, MAX_LONG_VALUE));
        this.value = new BigInteger("" + value);
        this.top = this.value.shiftRight(32).and(new BigInteger("4294967295")).longValue();
        this.bottom = this.value.and(new BigInteger("4294967295")).longValue();
    }


    /**
     * Create a UInt64 from two longs.
     * 
     * @param top
     *            Most significant 4 bytes.
     * @param bottom
     *            Least significant 4 bytes.
     */
    public UInt64 ( long top, long bottom ) {
        BigInteger a = new BigInteger("" + top);
        a = a.shiftLeft(32);
        a = a.add(new BigInteger("" + bottom));
        if ( 0 > a.compareTo(BigInteger.ZERO) )
            throw new NumberFormatException(String.format("%s is not between %s and %s.", a, MIN_VALUE, MAX_BIG_VALUE));
        if ( 0 < a.compareTo(MAX_BIG_VALUE) )
            throw new NumberFormatException(String.format("%s is not between %s and %s.", a, MIN_VALUE, MAX_BIG_VALUE));
        this.value = a;
        this.top = top;
        this.bottom = bottom;
    }


    /**
     * Create a UInt64 from a BigInteger
     * 
     * @param value
     *            Must be a valid BigInteger between MIN_VALUE&ndash;MAX_BIG_VALUE
     * @throws NumberFormatException
     *             if value is not an integer between MIN_VALUE and MAX_BIG_VALUE
     */
    public UInt64 ( BigInteger value ) {
        if ( null == value )
            throw new NumberFormatException(String.format("%s is not between %s and %s.", value, MIN_VALUE, MAX_BIG_VALUE));
        if ( 0 > value.compareTo(BigInteger.ZERO) )
            throw new NumberFormatException(String.format("%s is not between %s and %s.", value, MIN_VALUE, MAX_BIG_VALUE));
        if ( 0 < value.compareTo(MAX_BIG_VALUE) )
            throw new NumberFormatException(String.format("%s is not between %s and %s.", value, MIN_VALUE, MAX_BIG_VALUE));
        this.value = value;
        this.top = this.value.shiftRight(32).and(new BigInteger("4294967295")).longValue();
        this.bottom = this.value.and(new BigInteger("4294967295")).longValue();
    }


    /**
     * Create a UInt64 from a String.
     * 
     * @param value
     *            Must parse to a valid integer within MIN_VALUE&ndash;MAX_BIG_VALUE
     * @throws NumberFormatException
     *             if value is not an integer between MIN_VALUE and MAX_BIG_VALUE
     */
    public UInt64 ( String value ) {
        if ( null == value )
            throw new NumberFormatException(String.format("%s is not between %s and %s.", value, MIN_VALUE, MAX_BIG_VALUE));
        BigInteger a = new BigInteger(value);
        if ( 0 > a.compareTo(BigInteger.ZERO) )
            throw new NumberFormatException(String.format("%s is not between %s and %s.", value, MIN_VALUE, MAX_BIG_VALUE));
        if ( 0 < a.compareTo(MAX_BIG_VALUE) )
            throw new NumberFormatException(String.format("%s is not between %s and %s.", value, MIN_VALUE, MAX_BIG_VALUE));
        this.value = a;
        this.top = this.value.shiftRight(32).and(new BigInteger("4294967295")).longValue();
        this.bottom = this.value.and(new BigInteger("4294967295")).longValue();
    }


    /** The value of this as a BigInteger. */
    public BigInteger value () {
        return this.value;
    }


    /** The value of this as a byte. */
    @Override
    public byte byteValue () {
        return this.value.byteValue();
    }


    /** The value of this as a double. */
    @Override
    public double doubleValue () {
        return this.value.doubleValue();
    }


    /** The value of this as a float. */
    @Override
    public float floatValue () {
        return this.value.floatValue();
    }


    /** The value of this as a int. */
    @Override
    public int intValue () {
        return this.value.intValue();
    }


    /** The value of this as a long. */
    @Override
    public long longValue () {
        return this.value.longValue();
    }


    /** The value of this as a short. */
    @Override
    public short shortValue () {
        return this.value.shortValue();
    }


    /** Test two UInt64s for equality. */
    @Override
    public boolean equals ( Object o ) {
        return o instanceof UInt64 && this.value.equals( ( (UInt64) o ).value);
    }


    @Override
    public int hashCode () {
        return this.value.hashCode();
    }


    /**
     * Compare two UInt32s.
     * 
     * @return 0 if equal, -ve or +ve if they are different.
     */
    @Override
    public int compareTo ( UInt64 other ) {
        return this.value.compareTo(other.value);
    }


    /** The value of this as a string. */
    @Override
    public String toString () {
        return this.value.toString();
    }


    /**
     * Most significant 4 bytes.
     */
    public long top () {
        return this.top;
    }


    /**
     * Least significant 4 bytes.
     */
    public long bottom () {
        return this.bottom;
    }
}