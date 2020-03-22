package de.dosmike.sponge.toomuchstock.utils;

import java.math.BigDecimal;
import java.math.BigInteger;

public class VMath {

    public static int min(int... ints) {
        int v = ints[0];
        for (int i = 1; i < ints.length; i++) if (ints[i] < v) v = ints[i];
        return v;
    }
    public static long min(long... longs) {
        long v = longs[0];
        for (int i = 1; i < longs.length; i++) if (longs[i] < v) v = longs[i];
        return v;
    }
    public static float min(float... floats) {
        float v = floats[0];
        for (int i = 1; i < floats.length; i++) if (floats[i] < v) v = floats[i];
        return v;
    }
    public static double min(double... doubles) {
        double v = doubles[0];
        for (int i = 1; i < doubles.length; i++) if (doubles[i] < v) v = doubles[i];
        return v;
    }
    public static BigInteger min(BigInteger... ints) {
        BigInteger v = ints[0];
        for (int i = 1; i < ints.length; i++) if (ints[i].compareTo(v) < 0) v = ints[i];
        return v;
    }
    public static BigDecimal min(BigDecimal... decimals) {
        BigDecimal v = decimals[0];
        for (int i = 1; i < decimals.length; i++) if (decimals[i].compareTo(v) < 0) v = decimals[i];
        return v;
    }
    public static int max(int... ints) {
        int v = ints[0];
        for (int i = 1; i < ints.length; i++) if (ints[i] > v) v = ints[i];
        return v;
    }
    public static long max(long... longs) {
        long v = longs[0];
        for (int i = 1; i < longs.length; i++) if (longs[i] > v) v = longs[i];
        return v;
    }
    public static float max(float... floats) {
        float v = floats[0];
        for (int i = 1; i < floats.length; i++) if (floats[i] > v) v = floats[i];
        return v;
    }
    public static double max(double... doubles) {
        double v = doubles[0];
        for (int i = 1; i < doubles.length; i++) if (doubles[i] > v) v = doubles[i];
        return v;
    }
    public static BigInteger max(BigInteger... ints) {
        BigInteger v = ints[0];
        for (int i = 1; i < ints.length; i++) if (ints[i].compareTo(v) > 0) v = ints[i];
        return v;
    }
    public static BigDecimal max(BigDecimal... decimals) {
        BigDecimal v = decimals[0];
        for (int i = 1; i < decimals.length; i++) if (decimals[i].compareTo(v) > 0) v = decimals[i];
        return v;
    }
    public static int accu(int... ints) {
        int v = ints[0];
        for (int i = 1; i < ints.length; i++) v += ints[i];
        return v;
    }
    public static long accu(long... longs) {
        long v = longs[0];
        for (int i = 1; i < longs.length; i++) v += longs[i];
        return v;
    }
    public static float accu(float... floats) {
        float v = floats[0];
        for (int i = 1; i < floats.length; i++) v += floats[i];
        return v;
    }
    public static double accu(double... doubles) {
        double v = doubles[0];
        for (int i = 1; i < doubles.length; i++) v += doubles[i];
        return v;
    }
    public static BigInteger accu(BigInteger... ints) {
        BigInteger v = ints[0];
        for (int i = 1; i < ints.length; i++) v = v.add(ints[i]);
        return v;
    }
    public static BigDecimal accu(BigDecimal... decimals) {
        BigDecimal v = decimals[0];
        for (int i = 1; i < decimals.length; i++) v = v.add(decimals[i]);
        return v;
    }

}
