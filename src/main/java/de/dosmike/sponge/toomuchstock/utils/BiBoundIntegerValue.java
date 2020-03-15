package de.dosmike.sponge.toomuchstock.utils;

import org.jetbrains.annotations.Nullable;

public class BiBoundIntegerValue {

    private Integer min;
    private Integer max;
    private int value;

    /**
     * Create a bi-bound value with the set boundaries.
     * The initial value will be 0 if possible<br>
     * <code>initial = min > 0 ? min : (max < 0 ? max : 0)</code>
     * @param boundaryMin boundary 1 (inclusive)
     * @param boundaryMax boundary 2 (inclusive)
     */
    public BiBoundIntegerValue(@Nullable Integer boundaryMin, @Nullable Integer boundaryMax) {
        this.min = boundaryMin;
        this.max = boundaryMax;
        if (min != null && min > 0) value = min;
        else if (max != null && max < 0) value = max;
        else value = 0;
    }
    /**
     * Create a bi-bound value with the set boundaries and value
     * @param boundaryMin boundary 1 (inclusive)
     * @param boundaryMax boundary 2 (inclusive)
     * @param initialValue the initial value
     * @throws IllegalArgumentException if the initial value is not within range
     */
    public BiBoundIntegerValue(@Nullable Integer boundaryMin, @Nullable Integer boundaryMax, int initialValue) {
        this.min = boundaryMin;
        this.max = boundaryMax;
        this.value = initialValue;
        if ((min != null && value < min) || (max != null && value > max)) throw new IllegalArgumentException("Initial value not in range");
    }

    /**
     * Count up this value, but not beyond the maximum limit (inclusive).
     * @param amount the amount you initially want to increase the value by
     * @return the amount this value was actually increased by
     */
    public int increase(int amount) {
        if (amount < 0) return -decrease(-amount);
        int increase = max != null ? Math.min(max-value, amount) : amount;
        value += increase;
        return increase;
    }

    /**
     * Reduce this value, but not beyond the minimum limit (inclusive).
     * @param amount the amount you initially want to increase the value by
     * @return the amount this value was actually increased by
     */
    public int decrease(int amount) {
        if (amount < 0) return -increase(-amount);
        int decrease = min != null ? Math.min(value-min, amount) : amount;
        value -= decrease;
        return decrease;
    }

    /**
     * @return the current value
     */
    public int getValue() {
        return value;
    }

    /**
     * @return the minimum allowed value (inclusive) or null if no limit
     */
    public Integer getMin() {
        return min;
    }

    /**
     * @return the maximum allowed value (inclusive) or null if no limit
     */
    public Integer getMax() {
        return max;
    }

    /**
     * @return by how much this value can still be increased, or null if maximum is +INF
     */
    public Integer getIncreaseVolume() {
        return max != null ? max-value : null;
    }

    /**
     * @return by how much this value can still be decreased, or null if minimum is -INF
     */
    public Integer getDecreaseVolume() {
        return min != null ? value-min : null;
    }

    /**
     * Set a new value
     * @param value the value to set
     * @throws IllegalArgumentException if the value is not in range
     */
    public void setValue(int value) {

    }

}
