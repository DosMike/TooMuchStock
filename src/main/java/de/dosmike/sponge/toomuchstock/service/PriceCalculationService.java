package de.dosmike.sponge.toomuchstock.service;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.service.economy.Currency;

import java.math.BigDecimal;
import java.util.UUID;

public interface PriceCalculationService {

    /**
     * Get pricing information for players that seek to <b>purchase</b> items from this shop.<br>
     * Since prices are no longer linear with amount, this function returns the price for each amount from 1 up to {@code amount}.
     * The price is based on the current price tracking for global, optionally shop and optionally player.
     * Please use {@link TransactionPreview#confirm(int)} for actual transactions. Otherwise the prices wont change/update!
     * For price displays {@link #getCurrentPurchasePrice} might be more performant.
     * @param item the item to use the tracking for
     * @param amount the max amount of items to calculate prices for
     * @param staticPrice the static base-price this item shall use
     * @param currency the currency currently trading for income/spending limits
     * @param shopID the UUID of the shop, if this item is listed within a shop
     * @param playerID the UUID of the player that's seeking transaction (if applicable)
     * @return TransactionPreview with price listings and amount information
     */
    TransactionPreview getPurchaseInformation(ItemStack item, int amount, BigDecimal staticPrice, Currency currency, @Nullable UUID shopID, @Nullable UUID playerID);
    /**
     * Get pricing information for players that seek to <b>purchase</b> items from this shop.<br>
     * Since prices are no longer linear with amount, this function returns the price for each amount from 1 up to {@code amount}.
     * The price is based on the current price tracking for global, optionally shop and optionally player.
     * Please use {@link TransactionPreview#confirm(int)} for actual transactions. Otherwise the prices wont change/update!
     * For price displays {@link #getCurrentPurchasePrice} might be more performant.
     * @param item the item to use the tracking for
     * @param amount the max amount of items to calculate prices for
     * @param staticPrice the static base-price this item shall use
     * @param currency the currency currently trading for income/spending limits
     * @param shopID the UUID of the shop, if this item is listed within a shop
     * @param playerID the UUID of the player that's seeking transaction (if applicable)
     * @return TransactionPreview with price listings and amount information
     */
    TransactionPreview getPurchaseInformation(ItemStackSnapshot item, int amount, BigDecimal staticPrice, Currency currency, @Nullable UUID shopID, @Nullable UUID playerID);
    /**
     * Get pricing information for players that seek to <b>sell</b> items from this shop.<br>
     * Since prices are no longer linear with amount, this function returns the price for each amount from 1 up to {@code amount}.
     * The price is based on the current price tracking for global, optionally shop and optionally player.
     * Please use {@link TransactionPreview#confirm(int)} for actual transactions. Otherwise the prices wont change/update!
     * For price displays {@link #getCurrentSellingPrice} might be more performant.
     * @param item the item to use the tracking for
     * @param amount the max amount of items to calculate prices for
     * @param staticPrice the static base-price this item shall use
     * @param currency the currency currently trading for income/spending limits
     * @param shopID the UUID of the shop, if this item is listed within a shop
     * @param playerID the UUID of the player that's seeking transaction (if applicable)
     * @return TransactionPreview with price listings and amount information
     */
    TransactionPreview getSellingInformation(ItemStack item, int amount, BigDecimal staticPrice, Currency currency, @Nullable UUID shopID, @Nullable UUID playerID);
    /**
     * Get pricing information for players that seek to <b>sell</b> items from this shop.<br>
     * Since prices are no longer linear with amount, this function returns the price for each amount from 1 up to {@code amount}.
     * The price is based on the current price tracking for global, optionally shop and optionally player.
     * Please use {@link TransactionPreview#confirm(int)} for actual transactions. Otherwise the prices wont change/update!
     * For price displays {@link #getCurrentSellingPrice} might be more performant.
     * @param item the item to use the tracking for
     * @param amount the max amount of items to calculate prices for
     * @param staticPrice the static base-price this item shall use
     * @param currency the currency currently trading for income/spending limits
     * @param shopID the UUID of the shop, if this item is listed within a shop
     * @param playerID the UUID of the player that's seeking transaction (if applicable)
     * @return TransactionPreview with price listings and amount information
     */
    TransactionPreview getSellingInformation(ItemStackSnapshot item, int amount, BigDecimal staticPrice, Currency currency, @Nullable UUID shopID, @Nullable UUID playerID);

    /**
     * Get pricing information for players that seek to <b>purchase</b> items from this shop.<br>
     * In contrast to {@link #getPurchaseInformation} this only provides the price for the full
     * {@code amount} of items. <br>
     * <i>This is supposed to make displaying prices more performant, it's not meant for fetching final prices!</i><br>
     * If you're looking to make a transaction, please use {@link #getPurchaseInformation}
     * @param item the item to use the tracking for
     * @param amount the max amount of items to calculate prices for
     * @param staticPrice the static base-price this item shall use
     * @param shopID the UUID of the shop, if this item is listed within a shop
     * @param playerID the UUID of the player that's seeking transaction (if applicable)
     * @return The price for the specified amount of items, assuming the player could afford it.
     */
    BigDecimal getCurrentPurchasePrice(ItemStack item, int amount, BigDecimal staticPrice, @Nullable UUID shopID, @Nullable UUID playerID);

    /**
     * Get pricing information for players that seek to <b>purchase</b> items from this shop.<br>
     * In contrast to {@link #getPurchaseInformation} this only provides the price for the full
     * {@code amount} of items. <br>
     * <i>This is supposed to make displaying prices more performant, it's not meant for fetching final prices!</i><br>
     * If you're looking to make a transaction, please use {@link #getPurchaseInformation}
     * @param item the item to use the tracking for
     * @param amount the max amount of items to calculate prices for
     * @param staticPrice the static base-price this item shall use
     * @param shopID the UUID of the shop, if this item is listed within a shop
     * @param playerID the UUID of the player that's seeking transaction (if applicable)
     * @return The price for the specified amount of items, assuming the player could afford it.
     */
    BigDecimal getCurrentPurchasePrice(ItemStackSnapshot item, int amount, BigDecimal staticPrice, @Nullable UUID shopID, @Nullable UUID playerID);

    /**
     * Get pricing information for players that seek to <b>sell</b> items from this shop.<br>
     * In contrast to {@link #getSellingInformation} this only provides the price for the full
     * {@code amount} of items. <br>
     * <i>This is supposed to make displaying prices more performant, it's not meant for fetching final prices!</i><br>
     * If you're looking to make a transaction, please use {@link #getSellingInformation}
     * @param item the item to use the tracking for
     * @param amount the max amount of items to calculate prices for
     * @param staticPrice the static base-price this item shall use
     * @param shopID the UUID of the shop, if this item is listed within a shop
     * @param playerID the UUID of the player that's seeking transaction (if applicable)
     * @return The price for the specified amount of items, assuming the player could afford it.
     */
    BigDecimal getCurrentSellingPrice(ItemStack item, int amount, BigDecimal staticPrice, @Nullable UUID shopID, @Nullable UUID playerID);

    /**
     * Get pricing information for players that seek to <b>sell</b> items from this shop.<br>
     * In contrast to {@link #getSellingInformation} this only provides the price for the full
     * {@code amount} of items. <br>
     * <i>This is supposed to make displaying prices more performant, it's not meant for fetching final prices!</i><br>
     * If you're looking to make a transaction, please use {@link #getSellingInformation}
     * @param item the item to use the tracking for
     * @param amount the max amount of items to calculate prices for
     * @param staticPrice the static base-price this item shall use
     * @param shopID the UUID of the shop, if this item is listed within a shop
     * @param playerID the UUID of the player that's seeking transaction (if applicable)
     * @return The price for the specified amount of items, assuming the player could afford it.
     */
    BigDecimal getCurrentSellingPrice(ItemStackSnapshot item, int amount, BigDecimal staticPrice, @Nullable UUID shopID, @Nullable UUID playerID);

}
