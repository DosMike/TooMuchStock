package de.dosmike.sponge.toomuchstock.service;

import com.google.common.collect.ImmutableList;

import java.math.BigDecimal;

public interface TransactionPreview {

    void update();
    ImmutableList<BigDecimal> getCumulativeValueForItems();
    BigDecimal getCumulativeValueFor(int nItems);
    int getAffordableAmount();
    void confirm(int amount);

}
