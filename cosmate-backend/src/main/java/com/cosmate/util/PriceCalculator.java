package com.cosmate.util;

import com.cosmate.entity.OrderDetail;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PriceCalculator {
    public static BigDecimal calculateExtendPrice(OrderDetail detail, Integer extendDays) {
        if (detail.getRentAmount() == null || detail.getRentDay() == null || detail.getRentDay() <= 0) return BigDecimal.ZERO;
        BigDecimal rentAmount = detail.getRentAmount();
        int originalDays = detail.getRentDay();
        // rentDiscount is a percentage (e.g., 50 means subsequent days charged at 50% of pricePerDay)
        int rentDiscountInt = detail.getRentDiscount() == null ? 100 : detail.getRentDiscount();
        BigDecimal rentDiscountPct = new BigDecimal(rentDiscountInt);

        BigDecimal pricePerDay;
        if (originalDays <= 1) {
            pricePerDay = rentAmount;
        } else {
            BigDecimal multiplier = BigDecimal.ONE.add(new BigDecimal(originalDays - 1).multiply(rentDiscountPct).divide(new BigDecimal(100), 8, RoundingMode.HALF_UP));
            if (multiplier.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
            pricePerDay = rentAmount.divide(multiplier, 8, RoundingMode.HALF_UP);
        }

        BigDecimal subsequentRate = pricePerDay.multiply(rentDiscountPct).divide(new BigDecimal(100), 8, RoundingMode.HALF_UP);
        BigDecimal total = subsequentRate.multiply(new BigDecimal(extendDays));
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}

