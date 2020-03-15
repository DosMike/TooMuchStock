package de.dosmike.sponge.toomuchstock.service.impl;

import de.dosmike.sponge.toomuchstock.TooMuchStock;
import de.dosmike.sponge.toomuchstock.service.PriceCalculationService;
import de.dosmike.sponge.toomuchstock.service.TransactionPreview;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.service.economy.Currency;

import java.math.BigDecimal;
import java.util.UUID;

/** meant to run as service singleton. PriceCalculator rebuilds every config reload */
public class PriceCalculationProvider implements PriceCalculationService {

    @Override
    public TransactionPreview getPurchaseInformation(ItemStack item, int amount, BigDecimal staticPrice, Currency currency, @Nullable UUID shopID, @Nullable UUID playerID) {
        return TooMuchStock.getPriceCalculator().getPurchaseInformation(item, amount, staticPrice, currency, shopID, playerID);
    }

    @Override
    public TransactionPreview getPurchaseInformation(ItemStackSnapshot item, int amount, BigDecimal staticPrice, Currency currency, @Nullable UUID shopID, @Nullable UUID playerID) {
        return TooMuchStock.getPriceCalculator().getPurchaseInformation(item, amount, staticPrice, currency, shopID, playerID);
    }

    @Override
    public TransactionPreview getSellingInformation(ItemStack item, int amount, BigDecimal staticPrice, Currency currency, @Nullable UUID shopID, @Nullable UUID playerID) {
        return TooMuchStock.getPriceCalculator().getSellingInformation(item, amount, staticPrice, currency, shopID, playerID);
    }

    @Override
    public TransactionPreview getSellingInformation(ItemStackSnapshot item, int amount, BigDecimal staticPrice, Currency currency, @Nullable UUID shopID, @Nullable UUID playerID) {
        return TooMuchStock.getPriceCalculator().getSellingInformation(item, amount, staticPrice, currency, shopID, playerID);
    }

    @Override
    public BigDecimal getCurrentPurchasePrice(ItemStack item, int amount, BigDecimal staticPrice, @Nullable UUID shopID, @Nullable UUID playerID) {
        return TooMuchStock.getPriceCalculator().getCurrentPurchasePrice(item, amount, staticPrice, shopID, playerID);
    }

    @Override
    public BigDecimal getCurrentPurchasePrice(ItemStackSnapshot item, int amount, BigDecimal staticPrice, @Nullable UUID shopID, @Nullable UUID playerID) {
        return TooMuchStock.getPriceCalculator().getCurrentPurchasePrice(item, amount, staticPrice, shopID, playerID);
    }

    @Override
    public BigDecimal getCurrentSellingPrice(ItemStack item, int amount, BigDecimal staticPrice, @Nullable UUID shopID, @Nullable UUID playerID) {
        return TooMuchStock.getPriceCalculator().getCurrentSellingPrice(item, amount, staticPrice, shopID, playerID);
    }

    @Override
    public BigDecimal getCurrentSellingPrice(ItemStackSnapshot item, int amount, BigDecimal staticPrice, @Nullable UUID shopID, @Nullable UUID playerID) {
        return TooMuchStock.getPriceCalculator().getCurrentSellingPrice(item, amount, staticPrice, shopID, playerID);
    }
}
