package com.cosmate.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateCancellationPolicyRequest {
    private Integer providerId;
    private Integer minHoursBefore;
    private Integer maxHoursBefore;
    private String penaltyType;
    private BigDecimal penaltyValue;
    private String description;
}

