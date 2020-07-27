package de.dosmike.sponge.toomuchstock.maths;

import com.google.common.base.Objects;
import com.google.common.reflect.TypeToken;
import de.dosmike.sponge.toomuchstock.TooMuchStock;
import de.dosmike.sponge.toomuchstock.utils.*;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.service.economy.Currency;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class ItemTracker {

    private static final int stonkDuration = 25; //minutes
    private Stonks stonks = new Stonks(stonkDuration);

    /** Checks whether the supplied item stack will be affected by this tracker */
    private ApplicabilityFilters<?> applicabilityFilter;
    /** Stores the name of a item definition or item type for serialization */
    private String filterName = "Custom";

    /**
     * From the players view income is positive, so the max value is the amount
     * players can earn by selling items.
     */
    private Map<Currency, BiBoundBigDecimalValue> incomeLimit = new HashMap<>();
    /**
     * From the players view income is positive, so the max value is the amount
     * players can spend on buying items.
     */
    private Map<Currency, BiBoundBigDecimalValue> spendingLimit = new HashMap<>();
    /**
     * Too keep signs in line with the income limit, a player can sell a total of
     * itemLimit#getMax() items or buy a total of itemBuyLimit#getMin()
     */
    private BiBoundIntegerValue itemBuyLimit;
    /**
     * Too keep signs in line with the income limit, a player can buy a total
     * of itemSellLimit#getMax()
     */
    private BiBoundIntegerValue itemSellLimit;

    /**
     * The rate at which the price for this item goes down every time a single item
     * is sold to an admin shop
     */
    private double growthRate;

    /**
     * The rate at which the price for this item goes down every time a single item
     * is sold to an admin shop
     */
    private double decayRate;

    /**
     * The half life for the price discrepancy (based on growthRate and decayRate)
     * returning to the initial price in minutes is set by the configuration.
     * In order to update the price every minute the decay constant (lambda) is
     * required to calculate N(t+1) = N(t) * e ^ (-lambda * 1).
     * From the half-life the decay constant is calculated as lambda = ln(2) / halfLife
     */
    private double decayConstant;
    /** Configuration value */
    private long halfLife;

    /**
     * In order to keep resales at bay the dispersion devaluation is a factor [1..0]
     * that's multiplied onto the price tag every time a player sells items to
     * admin shops.
     */
    private double dispersionDevaluation;

    /**
     * The current price discrepancy to the price.
     * This means the price to use is price*(discrepancy+1).
     * The rance for this value should never drop below 0
     */
    private double discrepancy;

    public Predicate<ItemStackSnapshot> getApplicabilityFilter() {
        return applicabilityFilter;
    }
    String getApplicabilityFilterName() {
        return filterName;
    }

    /**
     * Creates a new tracker with the same configuration, but the supplied applicability filter
     */
    public ItemTracker newTracker(ApplicabilityFilters<?> filter) {
        ItemTracker copy = new ItemTracker();
        copy.applicabilityFilter = filter;
        copy.filterName = filterName;
        copy.decayConstant = decayConstant;
        copy.decayRate = decayRate;
        copy.discrepancy = 0;
        copy.dispersionDevaluation = 0;
        copy.growthRate = growthRate;
        for (Map.Entry<Currency, BiBoundBigDecimalValue> e : incomeLimit.entrySet()) {
            copy.incomeLimit.put(e.getKey(), new BiBoundBigDecimalValue(BigDecimal.ZERO, e.getValue().getMax()));
        }
        for (Map.Entry<Currency, BiBoundBigDecimalValue> e : spendingLimit.entrySet()) {
            copy.spendingLimit.put(e.getKey(), new BiBoundBigDecimalValue(BigDecimal.ZERO, e.getValue().getMax()));
        }
        copy.itemBuyLimit = new BiBoundIntegerValue(0, itemBuyLimit.getMax());
        copy.itemSellLimit = new BiBoundIntegerValue(0, itemSellLimit.getMax());
        return copy;
    }

    public ItemTracker clone() {
        return newTracker(applicabilityFilter);
    }

    /** pull values from another instance to minimize abuse on reload */
    public void merge(ItemTracker other) {
        decayConstant = other.decayConstant;
        decayRate = other.decayRate;
        growthRate = other.growthRate;
        dispersionDevaluation = other.dispersionDevaluation;

        incomeLimit.clear();
        incomeLimit.putAll(other.incomeLimit);
        spendingLimit.clear();
        spendingLimit.putAll(other.spendingLimit);

        int oldVal = itemBuyLimit.getValue();
        itemBuyLimit = new BiBoundIntegerValue(other.itemBuyLimit.getMin(), other.itemBuyLimit.getMax());
        try { itemBuyLimit.setValue(oldVal); } catch (Exception ignore) {}
        oldVal = itemSellLimit.getValue();
        itemSellLimit = new BiBoundIntegerValue(other.itemSellLimit.getMin(), other.itemSellLimit.getMax());
        try { itemSellLimit.setValue(oldVal); } catch (Exception ignore) {}
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemTracker that = (ItemTracker) o;
        return Objects.equal(applicabilityFilter, that.applicabilityFilter) &&
                filterName.equalsIgnoreCase(that.filterName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(applicabilityFilter, filterName);
    }

    /**
     * In order to update the price every minute the decay constant (lambda) is
     * required to calculate N(t+1) = N(t) * e ^ (-lambda * 1).
     * This method expects to be called once a minute, and the decayConstant to be
     * pre-calculated.
     */
    public void decayTick() {
        // finalize old value
        stonks.update(discrepancy);
        stonks.push();
        //decay discrepancy
        if (decayConstant == 0)
        {/* don't decay */}
        else if (Math.abs(discrepancy) > Double.MIN_VALUE) { //avoid unnecessary computations
            discrepancy = discrepancy * Math.exp(-decayConstant);
        }
        else
            discrepancy = 0d; // make it absolute 0, no uncertainty
        //update new value
        stonks.update(discrepancy);
    }
    /**
     * Performs a shortcut computation for the specified amount of ticks, instead of looping through
     * separate decayTick()s.
     * Similar to {@link #decayTick} the formula is N(t+x) = N(t) * e ^ (-lambda * x) where x is
     * the number of time steps.
     */
    public void decayTicks(long minutes) {
        //we can fast forward all values that do not get recorded into the visualization
        int reffedValueCount = stonkDuration+1; //because the last value still shows delta value
        if (minutes > reffedValueCount+1) { //skip atleast 2 values, otherwise just push
            long doForward = minutes-reffedValueCount;
            if (decayConstant == 0) {/* don't decay */}
            else if (Math.abs(discrepancy) > Double.MIN_VALUE) {
                discrepancy = discrepancy * Math.exp(-decayConstant * (minutes - doForward));
            }
            if (Math.abs(discrepancy) <= Double.MIN_VALUE) discrepancy = 0;
            minutes -= doForward;
        }
        //loop over the rest to actually refresh the stonk tracker/graphic
        for (int i = 0; i < minutes; i++) {
            decayTick(); //required to record stonks
        }
    }

    /**
     * Resets this manipulator
     */
    public void reset() {
        discrepancy = 0d;
        stonks = new Stonks(stonkDuration);
    }

    /** @return true if the history tracker for this tracker is filled with 1s */
    public boolean isIdle() {
        return stonks.isIdle();
    }

    /**
     * Does not change the discrepancy or any other factor within this tracker.
     * @return the current price multiplier as (discrepancy+1)
     */
    public double peek() {
        return 1.0+discrepancy;
    }
    public double getDecayRate() {
        return decayRate;
    }
    public double getGrowthRate() {
        return growthRate;
    }
    public double getDispersionDevaluation() {
        return dispersionDevaluation;
    }

    /**
     * After returning the multiplier this method decays the price about decay rate for the specified amount of times.
     * @param amount the amount of items that this modifier got applied to and thus the amount of times decay happens.
     * @return the current price multiplier as (discrepancy+1)
     */
    public double decay(int amount) {
        double multiplier = 1.0+discrepancy;
        discrepancy = DecayUtil.exponentialDecay(multiplier, decayRate, amount)-1.0;
        stonks.update(discrepancy);
        return multiplier;
    }
    /**
     * After returning the multiplier this method grows the price about growth rate for the specified amount of times.
     * @param amount the amount of items that this modifier got applied to and thus the amount of times growth happens.
     * @return the current price multiplier as (discrepancy+1)
     */
    public double grow(int amount) {
        double multiplier = 1.0+discrepancy;
        discrepancy = DecayUtil.exponentialGrowth(multiplier, growthRate, amount)-1.0;
        stonks.update(discrepancy);
        return multiplier;
    }

    /** true if this manipulator was specified in the config and is not derived
     * from a default manipulator */
    boolean derived = false;
    /** @return true if this manipulator was specified in the config and is not derived
     * from a default manipulator */
    public boolean isDerived() {
        return derived;
    }

    /** @return how many of this item are still purchasable within this tracker */
    public int getPurchaseItemCapacity() {
        Integer volume = itemBuyLimit.getIncreaseVolume();
        return volume == null ? Integer.MAX_VALUE : volume;
    }
    /** @return how many of this item are still distributable within this tracker */
    public int getDistributeItemCapacity() {
        Integer volume = itemSellLimit.getIncreaseVolume();
        return volume == null ? Integer.MAX_VALUE : volume;
    }
    /** @return how much money worth of this item is still purchasable within this tracker */
    public BigDecimal getPurchaseValueCapacity(Currency currency) {
        if (!spendingLimit.containsKey(currency)) return BigDecimal.valueOf(Integer.MAX_VALUE); //unregulated
        BigDecimal volume = spendingLimit.get(currency).getIncreaseVolume();
        return volume == null ? BigDecimal.valueOf(Integer.MAX_VALUE) : volume;
    }
    /** @return how much money worth of this item is still distributable within this tracker */
    public BigDecimal getDistributeValueCapacity(Currency currency) {
        if (!incomeLimit.containsKey(currency)) return BigDecimal.valueOf(Integer.MAX_VALUE); //unregulated
        BigDecimal volume = incomeLimit.get(currency).getIncreaseVolume();
        return volume == null ? BigDecimal.valueOf(Integer.MAX_VALUE) : volume;
    }

    public Stonks getStonks() {
        return stonks;
    }

    public static ItemTracker fromConfiguration(String filterName, ApplicabilityFilters<?> filter, ConfigurationNode node) throws ObjectMappingException {
        ItemTracker result = new ItemTracker();
        for (org.spongepowered.api.service.economy.Currency currency : TooMuchStock.getEconomy().getCurrencies()) {
            result.incomeLimit.put(currency, new BiBoundBigDecimalValue(
                    BigDecimal.ZERO,
                    node.getNode("incomeLimit").getNode(currency.getId()).getValue(TypeToken.of(BigDecimal.class)),
                    BigDecimal.ZERO));
            result.spendingLimit.put(currency, new BiBoundBigDecimalValue(
                    BigDecimal.ZERO,
                    node.getNode("spendingLimit").getNode(currency.getId()).getValue(TypeToken.of(BigDecimal.class)),
                    BigDecimal.ZERO));
        }
        result.itemBuyLimit = new BiBoundIntegerValue(
                0,
                node.getNode("aggregateAmount").isVirtual() ? null : node.getNode("aggregateAmount").getInt(),
                0
        );
        result.itemSellLimit = new BiBoundIntegerValue(
                0,
                node.getNode("disperseAmount").isVirtual() ? null : node.getNode("disperseAmount").getInt(),
                0
        );
        result.decayRate = node.getNode("priceDecay").getDouble();
        result.growthRate = node.getNode("priceIncrease").getDouble();
        result.halfLife = node.getNode("halflife").getInt();
        if (result.halfLife>0)
            result.decayConstant = Math.log(2)/result.halfLife;
        result.dispersionDevaluation = node.getNode("dispersionDevaluation").getDouble();

        result.applicabilityFilter = filter;
        result.filterName = filterName;
        return result;
    }
    public void toConfiguration(ConfigurationNode node) throws ObjectMappingException {
        for (Map.Entry<Currency, BiBoundBigDecimalValue> e : incomeLimit.entrySet()) {
            if (e.getValue().getMax()!=null)
                node.getNode("incomeLimit").getNode(e.getKey().getId()).setValue(TypeToken.of(BigDecimal.class), e.getValue().getMax());
        }
        for (Map.Entry<Currency, BiBoundBigDecimalValue> e : spendingLimit.entrySet()) {
            if (e.getValue().getMax()!=null)
                node.getNode("spendingLimit").getNode(e.getKey().getId()).setValue(TypeToken.of(BigDecimal.class), e.getValue().getMax());
        }
        if (itemBuyLimit.getMax() != null)
            node.getNode("aggregateAmount").setValue(itemBuyLimit.getMax());
        if (itemSellLimit.getMax() != null)
            node.getNode("disperseAmount").setValue(itemSellLimit.getMax());
        node.getNode("priceDecay").setValue(decayRate);
        node.getNode("priceIncrease").setValue(growthRate);
        node.getNode("halflife").setValue(halfLife);
        node.getNode("dispersionDevaluation").setValue(dispersionDevaluation);
    }

    @Override
    public String toString() {
        return "ItemTracker for " +
                applicabilityFilter;
    }

}
