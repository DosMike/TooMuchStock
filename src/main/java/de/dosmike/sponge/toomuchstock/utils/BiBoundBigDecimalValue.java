package de.dosmike.sponge.toomuchstock.utils;

import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;

public class BiBoundBigDecimalValue {

    private BigDecimal min;
    private BigDecimal max;
    private BigDecimal value;

    /**
     * Create a bi-bound value with the set boundaries.
     * The initial value will be 0 if possible<br>
     * <code>initial = min > 0 ? min : (max < 0 ? max : 0)</code>
     * Boundaries are optional, if a boundary is NULL it will be treated as -+ INF (because BigDecimal does not support that)
     * @param boundaryLow boundary 1 (inclusive)
     * @param boundaryHigh boundary 2 (inclusive)
     */
    public BiBoundBigDecimalValue(@Nullable BigDecimal boundaryLow, @Nullable BigDecimal boundaryHigh) {
        this.min = boundaryLow;
        this.max = boundaryHigh;
        if (min != null && min.compareTo(BigDecimal.ZERO)>0) value = min;
        else if (max != null && max.compareTo(BigDecimal.ZERO)<0) value = max;
        else value = BigDecimal.ZERO;
    }
    /**
     * Create a bi-bound value with the set boundaries and value
     * Boundaries are optional, if a boundary is NULL it will be treated as -+ INF (because BigDecimal does not support that)
     * @param boundaryMin boundary 1 (inclusive)
     * @param boundaryMax boundary 2 (inclusive)
     * @param initialValue the initial value
     * @throws IllegalArgumentException if the initial value is not within range
     */
    public BiBoundBigDecimalValue(BigDecimal boundaryMin, BigDecimal boundaryMax, BigDecimal initialValue) {
        this.min = boundaryMin;
        this.max = boundaryMax;
        this.value = initialValue;
        if ((min != null && value.compareTo(min) < 0) || (max != null && value.compareTo(max) > 0))
            throw new IllegalArgumentException("Initial value not in range");
    }

    /**
     * Count up this value, but not beyond the maximum limit (inclusive).
     * @param amount the amount you initially want to increase the value by
     * @return the amount this value was actually increased by
     */
    public BigDecimal increase(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) return decrease(amount.negate()).negate();
        BigDecimal increase = max != null ? max.subtract(value).min(amount) : amount;
        value = value.add(increase);
        return increase;
    }

    /**
     * Reduce this value, but not beyond the minimum limit (inclusive).
     * @param amount the amount you initially want to increase the value by
     * @return the amount this value was actually decreased by
     */
    public BigDecimal decrease(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) return increase(amount.negate()).negate();
        BigDecimal decrease = min != null ? value.subtract(min).min(amount) : amount;
        value = value.subtract(decrease);
        return decrease;
    }

    /**
     * @return the current value
     */
    public BigDecimal getValue() {
        return value;
    }

    /**
     * @return the minimum allowed value (inclusive) or null if no limit
     */
    public BigDecimal getMin() {
        return min;
    }

    /**
     * @return the maximum allowed value (inclusive) or null if no limit
     */
    public BigDecimal getMax() {
        return max;
    }

    /**
     * @return by how much this value can still be increased, or null if maximum is +INF
     */
    public BigDecimal getIncreaseVolume() {
        return max != null ? max.subtract(value) : null;
    }

    /**
     * @return by how much this value can still be decreased, or null if minimum is -INF
     */
    public BigDecimal getDecreaseVolume() {
        return min != null ? value.subtract(min) : null;
    }

    /**
     * Set a new value
     * @param value the value to set
     * @throws IllegalArgumentException if the value is not in range
     */
    public void setValue(BigDecimal value) {
        if (max != null && value.compareTo(max)>0) throw new IllegalArgumentException("Value exceeds maximum");
        if (min != null && value.compareTo(min)<0) throw new IllegalArgumentException("Value exceeds minimum");
        this.value = value;
    }

}
