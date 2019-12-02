package de.dosmike.sponge.toomuchstock.maths;

import com.google.common.collect.ImmutableList;
import de.dosmike.sponge.toomuchstock.utils.DecayUtil;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;

import java.math.BigDecimal;
import java.util.*;

public class PriceCalculator {

    PriceManipulator globalManip;
    Map<UUID, PriceManipulator> shopManips = new HashMap<>();
    Map<UUID, PriceManipulator> playerManips = new HashMap<>();

    public Result getPurchaseInformation(ItemStack item, int amount, BigDecimal staticPrice, Currency currency, @Nullable UUID shopID, @Nullable UUID playerID) {
        return getPurchaseInformation(item.createSnapshot(), amount, staticPrice, currency, shopID, playerID);
    }
    public Result getPurchaseInformation(ItemStackSnapshot item, int amount, BigDecimal staticPrice, Currency currency, @Nullable UUID shopID, @Nullable UUID playerID) {
        ItemTracker global = globalManip.getTrackerFor(item);
        ItemTracker shop = shopManips.get(shopID).getTrackerFor(item); //or null
        ItemTracker player = playerManips.get(playerID).getTrackerFor(item); //or null

        return new Result(global, shop, player, item, amount, true, staticPrice, getAccountBalance(playerID, currency));
    }
    public Result getSellingInformation(ItemStack item, int amount, BigDecimal staticPrice, Currency currency, @Nullable UUID shopID, @Nullable UUID playerID) {
        return getSellingInformation(item.createSnapshot(), amount, staticPrice, currency, shopID, playerID);
    }
    public Result getSellingInformation(ItemStackSnapshot item, int amount, BigDecimal staticPrice, Currency currency, @Nullable UUID shopID, @Nullable UUID playerID) {
        ItemTracker global = globalManip.getTrackerFor(item);
        ItemTracker shop = shopManips.get(shopID).getTrackerFor(item); //or null
        ItemTracker player = playerManips.get(playerID).getTrackerFor(item); //or null

        return new Result(global, shop, player, item, amount, false, staticPrice, getAccountCapacity(playerID, currency));
    }
    /**
     * this is for display only as it's less stress to compute. For the actual sell/purchase procedure,
     * please use {@link Result#confirm}, otherwise prices wont change! {@link Result}s are obtained from the
     * get_Information methods
     */
    public BigDecimal getCurrentPurchasePrice(ItemStack item, int amount, BigDecimal staticPrice, @Nullable UUID shopID, @Nullable UUID playerID) {
        return getCurrentPurchasePrice(item.createSnapshot(), amount, staticPrice, shopID, playerID);
    }
    /**
     * this is for display only as it's less stress to compute. For the actual sell/purchase procedure,
     * please use {@link Result#confirm}, otherwise prices wont change! {@link Result}s are obtained from the
     * get_Information methods
     */
    public BigDecimal getCurrentPurchasePrice(ItemStackSnapshot item, int amount, BigDecimal staticPrice, @Nullable UUID shopID, @Nullable UUID playerID) {
        ItemTracker global = globalManip.getTrackerFor(item);
        ItemTracker shop = shopManips.get(shopID).getTrackerFor(item); //or null
        ItemTracker player = playerManips.get(playerID).getTrackerFor(item); //or null

        double scale = 1d;
        scale = scale * DecayUtil.exponentialGrowth(global.peek(), global.getGrowthRate(), amount);
        if (shop != null) {
            scale = scale * DecayUtil.exponentialGrowth(shop.peek(), shop.getGrowthRate(), amount);
        }
        if (player != null) {
            scale = scale * DecayUtil.exponentialGrowth(player.peek(), player.getGrowthRate(), amount);
        }
        return BigDecimal.valueOf(scale).multiply(staticPrice);
    }
    /**
     * this is for display only as it's less stress to compute. For the actual sell/purchase procedure,
     * please use {@link Result#confirm}, otherwise prices wont change! {@link Result}s are obtained from the
     * get_Information methods
     */
    public BigDecimal getCurrentSellingPrice(ItemStack item, int amount, BigDecimal staticPrice, @Nullable UUID shopID, @Nullable UUID playerID) {
        return getCurrentSellingPrice(item.createSnapshot(), amount, staticPrice, shopID, playerID);
    }
    /**
     * this is for display only as it's less stress to compute. For the actual sell/purchase procedure,
     * please use {@link Result#confirm}, otherwise prices wont change! {@link Result}s are obtained from the
     * get_Information methods
     */
    public BigDecimal getCurrentSellingPrice(ItemStackSnapshot item, int amount, BigDecimal staticPrice, @Nullable UUID shopID, @Nullable UUID playerID) {
        ItemTracker global = globalManip.getTrackerFor(item);
        ItemTracker shop = shopManips.get(shopID).getTrackerFor(item); //or null
        ItemTracker player = playerManips.get(playerID).getTrackerFor(item); //or null

        double scale = 1d;
        scale = scale * DecayUtil.exponentialGrowth(global.peek(), global.getDecayRate(), amount);
        if (shop != null) {
            scale = scale * DecayUtil.exponentialGrowth(shop.peek(), shop.getDecayRate(), amount);
        }
        if (player != null) {
            scale = scale * DecayUtil.exponentialGrowth(player.peek(), player.getDecayRate(), amount);
        }
        return BigDecimal.valueOf(scale).multiply(staticPrice);
    }


    public static class Result {
        ItemTracker global;
        ItemTracker shop;
        ItemTracker player;
        ItemStackSnapshot item;
        int amount;
        boolean purchase;
        BigDecimal staticPrice;
        BigDecimal playerBalance;
        /** the value of &lt;index&gt; items */
        List<BigDecimal> nItemValue;
        int canAfford; //check the player balance
        /**
         * @param purchase if the player purchases items -> price will grow
         * @param playerBalance if selling this value should be the remaining capacity in the players account or NULL
         */
        public Result(ItemTracker global, @Nullable ItemTracker shop, @Nullable ItemTracker player, ItemStackSnapshot item, int amount, boolean purchase, BigDecimal staticPrice, @Nullable BigDecimal playerBalance) {
            this.global = global;
            this.shop = shop;
            this.player = player;
            this.item = item;
            this.amount = amount;
            this.staticPrice = staticPrice;
            this.playerBalance = playerBalance;
            nItemValue = new ArrayList<>(amount+1);
            canAfford = purchase ? 0 : Integer.MAX_VALUE;
            Collections.fill(nItemValue, BigDecimal.ZERO); //can't be empty for update()
            update();
        }
        /** calculated the end values for each amount and updated the can-afford value for the passed player balance. the player balance will not be updated */
        public void update() {
            List<Double> scales = new ArrayList<>(amount+1);
            Collections.fill(scales, 1d);
            scales = multiply(scales, purchase
                    ? DecayUtil.createGrowthMultiplicationVector(global.peek(), global.getGrowthRate(), amount)
                    : DecayUtil.createDecayMultiplicationVector(global.peek(), global.getDecayRate(), amount)
            );
            if (shop != null) {
                scales = multiply(scales, purchase
                        ? DecayUtil.createGrowthMultiplicationVector(shop.peek(), shop.getGrowthRate(), amount)
                        : DecayUtil.createDecayMultiplicationVector(shop.peek(), shop.getDecayRate(), amount)
                );
            }
            if (player != null) {
                scales = multiply(scales, purchase
                        ? DecayUtil.createGrowthMultiplicationVector(player.peek(), player.getGrowthRate(), amount)
                        : DecayUtil.createDecayMultiplicationVector(player.peek(), player.getDecayRate(), amount)
                );
            }
            BigDecimal thisValue;
            for (int i = 1; i < amount; i++) {
                thisValue = staticPrice.multiply(BigDecimal.valueOf(scales.get(i-1))); //price of 1 item is static price * multiplier after 0 iterations
                nItemValue.set(i, thisValue);
                if (playerBalance != null) {
                    if ((purchase && playerBalance.compareTo(thisValue)>=0) || //this player has more money then this amount will cost OR
                        (!purchase && thisValue.compareTo(playerBalance)<=0)) { //this value still fits in the player remaining account capacity
                        canAfford = i;
                    }
                }
            }
        }
        private static List<Double> multiply(List<Double> a, List<Double> b) {
            List<Double> elemsum = new ArrayList<>(a.size());
            for (int i = 0; i < a.size(); i++) elemsum.add(a.get(i)*b.get(i));
            return elemsum;
        }
        /** The index of the returned list matched the number of items for the value at the index */
        public ImmutableList<BigDecimal> getCumulativeValueForItems() {
            return ImmutableList.<BigDecimal>builder().addAll(nItemValue).build();
        }
        public BigDecimal getCumulativeValueFor(int nItems) {
            if (nItems < 0) throw new IllegalArgumentException("Can't get a value for negative quantities");
            if (nItems > amount) throw new IllegalArgumentException("The result was calculated for "+amount+" items, you asked for "+nItems);
            return nItemValue.get(nItems);
        }
        /** @return the amount of items the player can afford if this Result expresses a purchase. Otherwise the player balance was interpreted as
         * account limit and this equals the amount of items the player can sell, before the account hits it's limit.
         * In case playerBalance == null returns Integer.maxValue if selling and 0 if purchasing*/
        public int getAffordableAmount() {
            return canAfford;
        }
        /** confirm that the specified amount of items was just purchased or sold and to adjust price rates now */
        public void confirm(int amount) {
            if (amount < 1) return;
            if (purchase) {
                global.grow(amount);
                if (shop != null) shop.grow(amount);
                if (player != null) player.grow(amount);
            } else {
                global.decay(amount);
                if (shop != null) shop.decay(amount);
                if (player != null) player.decay(amount);
            }
            Sponge.getEventManager().post(new PriceUpdateEvent(item));
        }
    }

    private BigDecimal getAccountBalance(@Nullable UUID playerID, Currency currency) {
        //TODO
        return BigDecimal.ZERO;
    }
    /** @return null if accounts for this currency are not capped */
    private BigDecimal getAccountCapacity(@Nullable UUID playerID, Currency currency) {
        //TODO
        return BigDecimal.ZERO;
    }

}
