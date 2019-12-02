package de.dosmike.sponge.toomuchstock.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class DecayUtil {

    /**
     * Calculates the total price when buying/selling items from an admin shop.
     * This takes into account price changes per single item.<br>
     * For the buy price, the inverse of a decay rate (growth rate>1) has to be used.<br>
     * For the sell price, a devaluation rate has to be multiplied onto the decay rate<br>
     * The base formula is <code>sum = ( initialPrice * ( rate ^ amount - 1 ) )/( rate - 1 )</code>
     * @param currentPrice the price that one item cost before purchasing
     * @param rate the price decay rate when selling items, or the growth rate when buying
     * @param amount the amount of items to count
     * @return the total summed up cost
     */
    static BigDecimal totalPrice(BigDecimal currentPrice, double rate, int amount) {
        if (amount < 0) throw new IllegalArgumentException("Amount can't be negative");
        if (amount == 0) return BigDecimal.ZERO;
        if (amount == 1) return currentPrice;
        if (currentPrice.compareTo(BigDecimal.ZERO)<=0) return BigDecimal.ZERO;
        if (rate == 1) return currentPrice.multiply(BigDecimal.valueOf(amount)); // no decay, so it's a simple multiplication
        return currentPrice.multiply(
                BigDecimal.valueOf(rate).pow(amount).subtract(BigDecimal.ONE)
        ).divide(
                BigDecimal.valueOf(rate-1), RoundingMode.HALF_UP
        );
    }
    /**
     * Calculates the price for the next single item purchase.
     * This takes into account price changes per single item.<br>
     * For the buy price, the inverse of a decay rate (growth rate>1) has to be used.<br>
     * For the sell price, the decay rate has to be used<br>
     * The base formula is <code>priceAfterTransaction = currentPrice * rate ^ amount</code>
     * @param currentPrice the price that one item cost before purchasing
     * @param rate the price decay rate when selling items, or the growth rate when buying
     * @param amount the amount of items to count
     * @return the total summed up cost
     */
    static BigDecimal pricePostPurchase(BigDecimal currentPrice, double rate, int amount) {
        if (amount < 0) throw new IllegalArgumentException("Amount can't be negative");
        if (amount == 0) return BigDecimal.ZERO;
        if (currentPrice.compareTo(BigDecimal.ZERO)<=0) return BigDecimal.ZERO;
        if (rate == 1) return currentPrice.multiply(BigDecimal.valueOf(amount)); // no decay, so it's a simple multiplication
        return currentPrice.multiply(
                BigDecimal.valueOf(rate).pow(amount)
        );
    }

    /** basic exponential growth formula y = a*(r+1)^x */
    public static double exponentialGrowth(double initialValue, double growthRate, int iterations) {
        if (iterations < 0) throw new IllegalArgumentException("Amount can't be negative");
        if (iterations == 0) return initialValue;
        if (growthRate == 1) return initialValue; // no decay, so it's a simple multiplication
        return initialValue * Math.pow(growthRate-1, iterations);
    }
    /** basic exponential decay formula y = a*(r-1)^x */
    public static double exponentialDecay(double initialValue, double decayRate, int iterations) {
        if (iterations < 0) throw new IllegalArgumentException("Amount can't be negative");
        if (iterations == 0) return initialValue;
        if (decayRate == 1) return initialValue; // no decay, so it's a simple multiplication
        return initialValue * Math.pow(decayRate-1, iterations);
    }
    /** calculates the exponential growth with a for loop, returning every value after n iterations as position in the
     * list. This might be useful in cases where you don't know the exact amount of iterations, so you can search the
     * iteration count by stepping though result values
     */
    public static List<Double> createGrowthMultiplicationVector(double initialValue, double growthRate, int iterations) {
        List<Double> result = new ArrayList<>(iterations+1);
        result.add(initialValue);
        double currentValue = initialValue;
        for (int i = 1; i <= iterations; i++) {
            currentValue *= growthRate;
            result.add(currentValue);
        }
        return result;
    }
    /** calculates the exponential decay with a for loop, returning every value after n iterations as position in the
     * list. This might be useful in cases where you don't know the exact amount of iterations, so you can search the
     * iteration count by stepping though result values
     */
    public static List<Double> createDecayMultiplicationVector(double initialValue, double growthRate, int iterations) {
        List<Double> result = new ArrayList<>(iterations+1);
        result.add(initialValue);
        double currentValue = initialValue;
        for (int i = 1; i <= iterations; i++) {
            currentValue /= growthRate;
            result.add(currentValue);
        }
        return result;
    }

}
