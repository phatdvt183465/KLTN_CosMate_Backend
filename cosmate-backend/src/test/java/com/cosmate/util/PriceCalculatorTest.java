package com.cosmate.util;

import com.cosmate.entity.OrderDetail;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PriceCalculatorTest {
    @Test
    public void testExtendPriceScenario() {
        OrderDetail d = new OrderDetail();
        d.setRentDay(3); // original rent days
        d.setRentAmount(new BigDecimal("120")); // total rent amount for original order (pricePerDay 60 with discount 50% and 3 days)
        d.setRentDiscount(50); // subsequent days charged at 50%

        // In this example, pricePerDay should be 120 for first day and 30 for subsequent days? Let's rely on calculator
        BigDecimal extendPrice = PriceCalculator.calculateExtendPrice(d, 5);
        // Expected: subsequentRate = pricePerDay * 50% . If pricePerDay resolves to 60 then subsequentRate = 30 -> 30*5 = 150
        assertEquals(new BigDecimal("150.00"), extendPrice);
    }
}


