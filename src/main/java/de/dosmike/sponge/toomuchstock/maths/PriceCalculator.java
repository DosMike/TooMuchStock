package de.dosmike.sponge.toomuchstock.maths;

import com.google.common.collect.ImmutableList;
import de.dosmike.sponge.toomuchstock.TooMuchStock;
import de.dosmike.sponge.toomuchstock.service.PriceCalculationService;
import de.dosmike.sponge.toomuchstock.service.TransactionPreview;
import de.dosmike.sponge.toomuchstock.utils.DecayUtil;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.account.UniqueAccount;

import java.math.BigDecimal;
import java.util.*;

/**
 * This class is not suitable as service implementation as it gets
 * rebuilt with every config reload - unless i'd change service every time
 * the config reloads, but I like services being provided just once (also
 * good in case other plugins make assumptions)
 */
public class PriceCalculator implements PriceCalculationService {

    private PriceManipulator globalManip;
    private Map<UUID, PriceManipulator> shopManips = new HashMap<>();
    private Map<UUID, PriceManipulator> playerManips = new HashMap<>();

    private PriceManipulator shopBase;
    private PriceManipulator playerBase;

    private PriceCalculator(PriceManipulator baseGlobalManip, PriceManipulator baseShopManip, PriceManipulator basePlayerManip) {
        this.globalManip = baseGlobalManip;
        this.shopBase = baseShopManip;
        this.playerBase = basePlayerManip;
    }

    public void mergeManipulators(PriceManipulator baseGlobalUpdate, PriceManipulator baseShopUpdate, PriceManipulator basePlayerUpdate) {
        globalManip.merge(baseGlobalUpdate);
        shopBase.merge(baseShopUpdate);
        shopManips.values().forEach(manip->manip.merge(baseShopUpdate));
        playerBase.merge(basePlayerUpdate);
        playerManips.values().forEach(manip->manip.merge(basePlayerUpdate));
    }

    //Region builder
    public static class Builder {
        PriceManipulator manipulatorTemplateGlobal = null;
        PriceManipulator manipulatorTemplateShops = null;
        PriceManipulator manipulatorTemplatePlayer = null;
        private Builder() {}
        public Builder setGlobalManipulatorTemplate(PriceManipulator manipulator) {
            manipulatorTemplateGlobal = manipulator;
            return Builder.this;
        }
        public Builder setShopsManipulatorTemplate(PriceManipulator manipulator) {
            manipulatorTemplateGlobal = manipulator;
            return Builder.this;
        }
        public Builder setPlayerManipulatorTemplate(PriceManipulator manipulator) {
            manipulatorTemplateGlobal = manipulator;
            return Builder.this;
        }
        public PriceCalculator build() {
            if (manipulatorTemplateGlobal == null ||
                manipulatorTemplateShops == null ||
                manipulatorTemplatePlayer == null)
                throw new IllegalStateException("Not all manipulators were set");
            return new PriceCalculator(manipulatorTemplateGlobal, manipulatorTemplateShops, manipulatorTemplatePlayer);
        }
    }
    public static Builder builder() {
        return new Builder();
    }
    //endregion

    public void dumpBaseConfiguration(ConfigurationNode parent) throws ObjectMappingException {
        globalManip.toConfiguration(parent.getNode("global"));
        shopBase.toConfiguration(parent.getNode("shops"));
        playerBase.toConfiguration(parent.getNode("player"));
    }

    public Optional<ItemTracker> getGlobalTracker(ItemStackSnapshot item) {
        return globalManip.getIfCurrentlyTracked(item);
    }
    public Optional<ItemTracker> getShopTracker(UUID shop, ItemStackSnapshot item) {
        return Optional.ofNullable(shopManips.get(shop)).flatMap(manip->manip.getIfCurrentlyTracked(item));
    }
    public Optional<ItemTracker> getPlayerTracker(UUID player, ItemStackSnapshot item) {
        return Optional.ofNullable(playerManips.get(player)).flatMap(manip->manip.getIfCurrentlyTracked(item));
    }

    public Result getPurchaseInformation(ItemStack item, int amount, BigDecimal staticPrice, Currency currency, @Nullable UUID shopID, @Nullable UUID playerID) {
        return getPurchaseInformation(item.createSnapshot(), amount, staticPrice, currency, shopID, playerID);
    }
    public Result getPurchaseInformation(ItemStackSnapshot item, int amount, BigDecimal staticPrice, Currency currency, @Nullable UUID shopID, @Nullable UUID playerID) {
        ItemTracker global = globalManip.getTrackerFor(item);
        PriceManipulator manip = shopManips.get(shopID);
        if (manip == null) shopManips.put(shopID, manip = shopBase.clone());
        ItemTracker shop = manip.getTrackerFor(item); //or null
        manip = playerManips.get(shopID);
        if (manip == null) playerManips.put(shopID, manip = playerBase.clone());
        ItemTracker player = manip.getTrackerFor(item); //or null

        return new Result(global, shop, player, item, amount, true, staticPrice, currency, getAccountBalance(playerID, currency));
    }
    public Result getSellingInformation(ItemStack item, int amount, BigDecimal staticPrice, Currency currency, @Nullable UUID shopID, @Nullable UUID playerID) {
        return getSellingInformation(item.createSnapshot(), amount, staticPrice, currency, shopID, playerID);
    }
    public Result getSellingInformation(ItemStackSnapshot item, int amount, BigDecimal staticPrice, Currency currency, @Nullable UUID shopID, @Nullable UUID playerID) {
        ItemTracker global = globalManip.getTrackerFor(item);
        PriceManipulator manip = shopManips.get(shopID);
        if (manip == null) shopManips.put(shopID, manip = shopBase.clone());
        ItemTracker shop = manip.getTrackerFor(item); //or null
        manip = playerManips.get(shopID);
        if (manip == null) playerManips.put(shopID, manip = playerBase.clone());
        ItemTracker player = manip.getTrackerFor(item); //or null

        return new Result(global, shop, player, item, amount, false, staticPrice, currency, getAccountCapacity(playerID, currency));
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
        PriceManipulator manip = shopManips.get(shopID);
        if (manip == null) shopManips.put(shopID, manip = shopBase.clone());
        ItemTracker shop = manip.getTrackerFor(item); //or null
        manip = playerManips.get(shopID);
        if (manip == null) playerManips.put(shopID, manip = playerBase.clone());
        ItemTracker player = manip.getTrackerFor(item); //or null

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
        PriceManipulator manip = shopManips.get(shopID);
        if (manip == null) shopManips.put(shopID, manip = shopBase.clone());
        ItemTracker shop = manip.getTrackerFor(item); //or null
        manip = playerManips.get(shopID);
        if (manip == null) playerManips.put(shopID, manip = playerBase.clone());
        ItemTracker player = manip.getTrackerFor(item); //or null

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


    public static class Result implements TransactionPreview {
        ItemTracker global;
        ItemTracker shop;
        ItemTracker player;
        ItemStackSnapshot item;
        int amount;
        boolean purchase;
        BigDecimal staticPrice;
        BigDecimal playerBalance;
        Currency currency;
        /** the value of &lt;index&gt; items */
        List<BigDecimal> nItemValue;
        int canAfford; //check the player balance, shop eco limits and shop amount limits
        /**
         * @param purchase if the player purchases items -> price will grow
         * @param playerBalance if selling this value should be the remaining capacity in the players account or NULL
         */
        public Result(ItemTracker global, @Nullable ItemTracker shop, @Nullable ItemTracker player, ItemStackSnapshot item, int amount, boolean purchase, BigDecimal staticPrice, Currency currency, @Nullable BigDecimal playerBalance) {
            this.global = global;
            this.shop = shop;
            this.player = player;
            this.item = item;
            this.amount = amount;
            this.staticPrice = staticPrice;
            this.currency = currency;
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
            // max value is minimum available value over all trackers
            BigDecimal maxValue;
            // max amount is minimum available amount over all trackers
            int maxAmount;
            scales = multiply(scales, purchase
                    ? DecayUtil.createGrowthMultiplicationVector(global.peek(), global.getGrowthRate(), amount)
                    : DecayUtil.createDecayMultiplicationVector(global.peek(), global.getDecayRate(), amount)
            );
            if (purchase) {
                maxValue = global.getPurchaseValueCapacity(currency);
                maxAmount = global.getPurchaseItemCapacity();
            } else {
                maxValue = global.getDistributeValueCapacity(currency);
                maxAmount = global.getDistributeItemCapacity();
            }
            if (shop != null) {
                scales = multiply(scales, purchase
                        ? DecayUtil.createGrowthMultiplicationVector(shop.peek(), shop.getGrowthRate(), amount)
                        : DecayUtil.createDecayMultiplicationVector(shop.peek(), shop.getDecayRate(), amount)
                );
                if (purchase) {
                    BigDecimal cap = shop.getPurchaseValueCapacity(currency);
                    if (cap.compareTo(maxValue)<0) maxValue = cap;
                    int cnt = shop.getPurchaseItemCapacity();
                    if (cnt < maxAmount) maxAmount = cnt;
                } else {
                    BigDecimal cap = shop.getDistributeValueCapacity(currency);
                    if (cap.compareTo(maxValue)<0) maxValue = cap;
                    int cnt = shop.getDistributeItemCapacity();
                    if (cnt < maxAmount) maxAmount = cnt;
                }
            }
            if (player != null) {
                scales = multiply(scales, purchase
                        ? DecayUtil.createGrowthMultiplicationVector(player.peek(), player.getGrowthRate(), amount)
                        : DecayUtil.createDecayMultiplicationVector(player.peek(), player.getDecayRate(), amount)
                );
                if (purchase) {
                    BigDecimal cap = player.getPurchaseValueCapacity(currency);
                    if (cap.compareTo(maxValue)<0) maxValue = cap;
                    int cnt = player.getPurchaseItemCapacity();
                    if (cnt < maxAmount) maxAmount = cnt;
                } else {
                    BigDecimal cap = player.getDistributeValueCapacity(currency);
                    if (cap.compareTo(maxValue)<0) maxValue = cap;
                    int cnt = player.getDistributeItemCapacity();
                    if (cnt < maxAmount) maxAmount = cnt;
                }
            }
            BigDecimal thisValue;
            for (int i = 1; i < amount; i++) {
                thisValue = staticPrice.multiply(BigDecimal.valueOf(scales.get(i-1))); //price of 1 item is static price * multiplier after 0 iterations
                nItemValue.set(i, thisValue);
                if (purchase) {
                    if ((playerBalance == null || playerBalance.compareTo(thisValue) >= 0) &&
                        amount <= maxAmount && thisValue.compareTo(maxValue) <= 0) {
                        canAfford = i;
                    }
                } else {
                    //this value still fits in the player remaining account capacity
                    if ((playerBalance == null || thisValue.compareTo(playerBalance)<=0) &&
                        amount <= maxAmount && thisValue.compareTo(maxValue) <= 0) {
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
         * In case playerBalance == null returns Integer.maxValue if selling and 0 if purchasing.
         * This value takes into account the player balance as well as the  maximum trade-capacity
         * of the server/shop/player through item count and economy value. You can not confirm an amount greater
         * than this!
         */
        public int getAffordableAmount() {
            return canAfford;
        }
        /** confirm that the specified amount of items was just purchased or sold and to adjust price rates now
         * @throws IllegalArgumentException if amount is greater than {@link #getAffordableAmount()}
         */
        public void confirm(int amount) {
            if (amount > canAfford)
                throw new IllegalArgumentException("The specified amount can not be traded!");
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
        Optional<UniqueAccount> account = TooMuchStock.getEconomy().getOrCreateAccount(playerID);
        return account.map(uniqueAccount -> uniqueAccount.getBalance(currency)).orElse(BigDecimal.ZERO);
    }
    /** @return null if accounts for this currency are not capped */
    private BigDecimal getAccountCapacity(@Nullable UUID playerID, Currency currency) {
        return null; //don't know how to get that
    }

}
