package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class SubscriptionPlanResponse {
    private Integer id;
    private String name;
    private String billingCycle;
    private Integer cycleMonths;
    private BigDecimal price;
    private Boolean isActive;
    private Integer monthlyToken;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
