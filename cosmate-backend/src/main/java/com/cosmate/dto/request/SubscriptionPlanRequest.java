package com.cosmate.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SubscriptionPlanRequest {
    private String name;
    private String billingCycle; // MONTH, QUARTER, YEAR
    private Integer cycleMonths;
    private BigDecimal price;
    private Boolean isActive;
    private String description;
}
