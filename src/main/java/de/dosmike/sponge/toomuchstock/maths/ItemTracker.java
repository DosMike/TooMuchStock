package de.dosmike.sponge.toomuchstock.maths;

import com.google.common.reflect.TypeToken;
import de.dosmike.sponge.toomuchstock.TooMuchStock;
import de.dosmike.sponge.toomuchstock.utils.BiBoundBigDecimalValue;
import de.dosmike.sponge.toomuchstock.utils.BiBoundIntegerValue;
import de.dosmike.sponge.toomuchstock.utils.DecayUtil;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.service.economy.Currency;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class ItemTracker {

    /** Checks whether the supplied item stack will be affected by this tracker */
    private Predicate<ItemStackSnapshot> applicabilityFilter;

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
    private long decayConstant;

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

    /**
     * Creates a new tracker with the same configuration, but the supplied applicability filter
     */
    public ItemTracker newTracker(Predicate<ItemStackSnapshot> filter) {
        ItemTracker copy = new ItemTracker();
        copy.applicabilityFilter = filter;
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

    /**
     * In order to update the price every minute the decay constant (lambda) is
     * required to calculate N(t+1) = N(t) * e ^ (-lambda * 1).
     * This method expects to be called once a minute, and the decayConstant to be
     * pre-calculated.
     */
    public void decayTick() {
        if (decayConstant == 0) discrepancy = 0d;
        else if (discrepancy != 0) { //avoid unnecessary computations
            discrepancy = discrepancy * Math.exp(-decayConstant);
        }
    }
    /**
     * Performs a shortcut computation for the specified amount of ticks, instead of looping through
     * separate decayTick()s.
     * Similar to {@link #decayTick} the formula is N(t+x) = N(t) * e ^ (-lambda * x) where x is
     * the number of time steps.
     */
    public void decayTicks(long minutes) {
        if (decayConstant == 0) discrepancy = 0d;
        else if (discrepancy != 0) {
            discrepancy = discrepancy * Math.exp(-decayConstant*minutes);
        }
    }

    /**
     * Resets this manipulator
     */
    public void reset() {
        discrepancy = 0d;
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
        if (!spendingLimit.containsKey(currency)) return BigDecimal.valueOf(Double.POSITIVE_INFINITY); //unregulated
        BigDecimal volume = spendingLimit.get(currency).getIncreaseVolume();
        return volume == null ? BigDecimal.valueOf(Double.POSITIVE_INFINITY) : volume;
    }
    /** @return how much money worth of this item is still distributable within this tracker */
    public BigDecimal getDistributeValueCapacity(Currency currency) {
        if (!incomeLimit.containsKey(currency)) return BigDecimal.valueOf(Double.POSITIVE_INFINITY); //unregulated
        BigDecimal volume = incomeLimit.get(currency).getIncreaseVolume();
        return volume == null ? BigDecimal.valueOf(Double.POSITIVE_INFINITY) : volume;
    }

    public static ItemTracker fromConfiguration(Predicate<ItemStackSnapshot> filter, ConfigurationNode node) throws ObjectMappingException {
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
        result.decayConstant = node.getNode("halflife").getInt();
        result.dispersionDevaluation = node.getNode("dispersionDevaluation").getDouble();

        result.applicabilityFilter = filter;
        return result;
    }

    public ItemTracker clone() {
        return newTracker(applicabilityFilter);
    }

    @Override
    public String toString() {
        return "ItemTracker for " +
                applicabilityFilter;
    }
}
