package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ProviderSubscriptionResponse {
    private Integer id;
    private Integer providerId;
    private Integer subscriptionPlanId;
    private String name;
    private String duration;
    private BigDecimal price;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String status;
    private Integer transactionId;
    private LocalDateTime createdAt;
}
