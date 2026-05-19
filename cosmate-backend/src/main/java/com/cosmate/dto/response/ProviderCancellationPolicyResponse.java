package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ProviderCancellationPolicyResponse {
    private Integer id;
    private Integer providerId;
    private Integer minHoursBefore;
    private Integer maxHoursBefore;
    private String penaltyType;
    private BigDecimal penaltyValue;
    private String description;
}

