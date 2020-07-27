package de.dosmike.sponge.toomuchstock.maths;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import de.dosmike.sponge.toomuchstock.TooMuchStock;
import de.dosmike.sponge.toomuchstock.service.PriceCalculationService;
import de.dosmike.sponge.toomuchstock.service.TransactionPreview;
import de.dosmike.sponge.toomuchstock.utils.DecayUtil;
import de.dosmike.sponge.toomuchstock.utils.VMath;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.account.UniqueAccount;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
            manipulatorTemplateShops = manipulator;
            return Builder.this;
        }
        public Builder setPlayerManipulatorTemplate(PriceManipulator manipulator) {
            manipulatorTemplatePlayer = manipulator;
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

    //expected to be called once a minute
    public void thinkTick() {
        Set<UUID> staleShopManips = new HashSet<>();
        Set<UUID> stalePlayerManips = new HashSet<>();
        globalManip.think();
        for (Map.Entry<UUID, PriceManipulator> e : shopManips.entrySet()) {
            e.getValue().think();
            if (e.getValue().isIdle()) staleShopManips.add(e.getKey());
        }
        for (Map.Entry<UUID, PriceManipulator> e : playerManips.entrySet()) {
            e.getValue().think();
            if (e.getValue().isIdle()) stalePlayerManips.add(e.getKey());
        }
        for (UUID id : staleShopManips) shopManips.remove(id);
        for (UUID id : stalePlayerManips) playerManips.remove(id);
    }

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
        ItemTracker shop = shopManips.computeIfAbsent(shopID, (id)->shopBase.clone())
                .getTrackerFor(item); //or null
        ItemTracker player = playerManips.computeIfAbsent(playerID, (id)->playerBase.clone())
                .getTrackerFor(item); //or null

        return new Result(global, shop, player, item, amount, true, staticPrice, currency, getAccountBalance(playerID, currency));
    }
    public Result getSellingInformation(ItemStack item, int amount, BigDecimal staticPrice, Currency currency, @Nullable UUID shopID, @Nullable UUID playerID) {
        return getSellingInformation(item.createSnapshot(), amount, staticPrice, currency, shopID, playerID);
    }
    public Result getSellingInformation(ItemStackSnapshot item, int amount, BigDecimal staticPrice, Currency currency, @Nullable UUID shopID, @Nullable UUID playerID) {
        ItemTracker global = globalManip.getTrackerFor(item);
        ItemTracker shop = shopManips.computeIfAbsent(shopID, (id)->shopBase.clone())
                .getTrackerFor(item); //or null
        ItemTracker player = playerManips.computeIfAbsent(playerID, (id)->playerBase.clone())
                .getTrackerFor(item); //or null

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
        if (amount == 0) return BigDecimal.ZERO;
        ItemTracker global = globalManip.getTrackerFor(item);
        ItemTracker shop = shopManips.computeIfAbsent(shopID, (id)->shopBase.clone())
                .getTrackerFor(item); //or null
        ItemTracker player = playerManips.computeIfAbsent(playerID, (id)->playerBase.clone())
                .getTrackerFor(item); //or null

        // We want the scale FOR amount items, not AFTER amount items, so subtract 1
        List<Double> mods = DecayUtil.createGrowthMultiplicationVector(global.peek(), global.getGrowthRate(), amount-1);
        if (shop != null) {
            mods = multiply(mods, DecayUtil.createGrowthMultiplicationVector(shop.peek(), shop.getGrowthRate(), amount-1));
        }
        if (player != null) {
            mods = multiply(mods, DecayUtil.createGrowthMultiplicationVector(player.peek(), player.getGrowthRate(), amount-1));
        }
        double scale = mods.stream().reduce(Double::sum).get();
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
        if (amount == 0) return BigDecimal.ZERO;
        ItemTracker global = globalManip.getTrackerFor(item);
        ItemTracker shop = shopManips.computeIfAbsent(shopID, (id)->shopBase.clone())
                .getTrackerFor(item); //or null
        ItemTracker player = playerManips.computeIfAbsent(playerID, (id)->playerBase.clone())
                .getTrackerFor(item); //or null

        // We want the scale FOR amount items, not AFTER amount items, so subtract 1
        List<Double> mods = DecayUtil.createDecayMultiplicationVector(global.peek(), global.getDecayRate(), amount-1);
        if (shop != null) {
            mods = multiply(mods, DecayUtil.createDecayMultiplicationVector(shop.peek(), shop.getDecayRate(), amount-1));
        }
        if (player != null) {
            mods = multiply(mods, DecayUtil.createDecayMultiplicationVector(player.peek(), player.getDecayRate(), amount-1));
        }
        double scale = mods.stream().reduce(Double::sum).get();
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
        int limitAccount, limitCurrency, limitItems;
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
            this.purchase = purchase;
            this.staticPrice = staticPrice;
            this.currency = currency;
            this.playerBalance = playerBalance;
            nItemValue = new ArrayList<>(amount+1);
            for (int i=0;i<=amount;i++) nItemValue.add(BigDecimal.ZERO); //can't be empty for update()
            canAfford = purchase ? 0 : Integer.MAX_VALUE;
            limitAccount = purchase ? 0 : Integer.MAX_VALUE;
            limitCurrency = purchase ? 0 : Integer.MAX_VALUE;
            limitItems = purchase ? 0 : Integer.MAX_VALUE;
            update();
        }
        /** calculated the end values for each amount and updated the can-afford value for the passed player balance. the player balance will not be updated */
        public void update() {
            //hold multiplicators for (index+1)th item
            List<Double> scales; // indices: 0, 1, ..., AMOUNT-1

            // max value is minimum available value over all trackers
            BigDecimal maxValue;
            // max amount is minimum available amount over all trackers

            if (purchase) {
                scales = DecayUtil.createGrowthMultiplicationVector(global.peek(), global.getGrowthRate(), amount-1);
                maxValue = global.getPurchaseValueCapacity(currency);
                limitItems = global.getPurchaseItemCapacity();
            } else {
                scales = DecayUtil.createDecayMultiplicationVector(global.peek(), global.getDecayRate(), amount-1);
                maxValue = global.getDistributeValueCapacity(currency);
                limitItems = global.getDistributeItemCapacity();
            }
            if (shop != null) {
                if (purchase) {
                    scales = multiply(scales, DecayUtil.createGrowthMultiplicationVector(shop.peek(), shop.getGrowthRate(), amount-1));
                    BigDecimal cap = shop.getPurchaseValueCapacity(currency);
                    if (cap.compareTo(maxValue)<0) maxValue = cap;
                    int cnt = shop.getPurchaseItemCapacity();
                    if (cnt < limitItems) limitItems = cnt;
                } else {
                    scales = multiply(scales, DecayUtil.createDecayMultiplicationVector(shop.peek(), shop.getDecayRate(), amount-1));
                    BigDecimal cap = shop.getDistributeValueCapacity(currency);
                    if (cap.compareTo(maxValue)<0) maxValue = cap;
                    int cnt = shop.getDistributeItemCapacity();
                    if (cnt < limitItems) limitItems = cnt;
                }
            }
            if (player != null) {
                if (purchase) {
                    scales = multiply(scales, DecayUtil.createGrowthMultiplicationVector(player.peek(), player.getGrowthRate(), amount-1));
                    BigDecimal cap = player.getPurchaseValueCapacity(currency);
                    if (cap.compareTo(maxValue)<0) maxValue = cap;
                    int cnt = player.getPurchaseItemCapacity();
                    if (cnt < limitItems) limitItems = cnt;
                } else {
                    scales = multiply(scales, DecayUtil.createDecayMultiplicationVector(player.peek(), player.getDecayRate(), amount-1));
                    BigDecimal cap = player.getDistributeValueCapacity(currency);
                    if (cap.compareTo(maxValue)<0) maxValue = cap;
                    int cnt = player.getDistributeItemCapacity();
                    if (cnt < limitItems) limitItems = cnt;
                }
            }
            BigDecimal thisValue; BigDecimal accumulativeMultiplier = BigDecimal.ZERO;
            for (int i = 1; i <= amount; i++) {
                accumulativeMultiplier = accumulativeMultiplier.add(BigDecimal.valueOf(scales.get(i-1))); //price of 1 item is static price * multiplier after 0 iterations
                thisValue = staticPrice.multiply(accumulativeMultiplier);
                nItemValue.set(i, thisValue);
                if (purchase) {
                    if ((playerBalance == null || playerBalance.compareTo(thisValue) >= 0)) {
                        limitAccount = i;
                    }
                    if (thisValue.compareTo(maxValue) <= 0) {
                        limitCurrency = i;
                    }
                } else {
                    //this value still fits in the player remaining account capacity
                    if ((playerBalance == null || thisValue.compareTo(playerBalance)<=0)) {
                        limitAccount = i;
                    }
                    if (thisValue.compareTo(maxValue) <= 0) {
                        limitCurrency = i;
                    }
                }
            }
            canAfford = VMath.min(limitAccount, limitItems, limitCurrency, amount);
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

        @Override
        public int getLimitAccount() {
            return limitAccount;
        }

        @Override
        public int getLimitItemTransactions() {
            return limitItems;
        }

        @Override
        public int getLimitCurrencyTransactions() {
            return limitCurrency;
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

    private static List<Double> multiply(List<Double> a, List<Double> b) {
        assert a.size()==b.size();
        List<Double> elemsum = new ArrayList<>(a.size());
        for (int i = 0; i < a.size(); i++) elemsum.add(a.get(i)*b.get(i));
        return elemsum;
    }

//    public void unloadPlayerState(UUID player) {
//        PriceManipulator manipulator = playerManips.remove(player);
//        if (manipulator==null) return;
//        manipulator.cleanUp();
//        try {
//            Path playerCache = TooMuchStock.getCacheDirectory()
//                    .resolve("players");
//            Files.createDirectories(playerCache);
//            playerCache = playerCache.resolve(player.toString().replace("-", "")+".bin");
//
//            HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
//                    .setPath(playerCache)
//                    .build();
//            CommentedConfigurationNode node = loader.createEmptyNode();
//            manipulator.toConfiguration(node);
//            node.getNode("StateTime").setValue(System.currentTimeMillis());
//            loader.save(node);
//        } catch (Throwable t) {
//            TooMuchStock.w("Could not dump player state for %s", player.toString());
//            t.printStackTrace();
//        }
//    }
//    public void loadPlayerState(UUID player) {
//        try {
//            Path playerCache = TooMuchStock.getCacheDirectory()
//                    .resolve("players")
//                    .resolve(player.toString().replace("-", "")+".bin");
//            if (!Files.exists(playerCache)) return; //nothing caches
//
//            HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
//                    .setPath(playerCache)
//                    .build();
//            CommentedConfigurationNode node = loader.load();
//            PriceManipulator manipulator = PriceManipulator.fromConfiguration(node);
//            long stateTime = node.getNode("StateTime").getLong();
//            loader.save(node);
//            //patch in new base tracker values
//            manipulator.merge(playerBase);
//            manipulator.bigBrainTime(stateTime);
//            manipulator.cleanUp();
//            playerManips.put(player, manipulator);
//        } catch (Throwable t) {
//            TooMuchStock.w("Could not read cached player state for %s. State is reset", player.toString());
//            t.printStackTrace();
//        }
//    }

}
