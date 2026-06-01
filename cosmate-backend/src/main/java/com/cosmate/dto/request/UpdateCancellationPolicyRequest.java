package com.cosmate.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateCancellationPolicyRequest {
    // Only include fields that are allowed to be updated via PUT
    @Schema(description = "Penalty type", allowableValues = {"NONE", "PERCENT", "FIXED"})
    private String penaltyType;
    private BigDecimal penaltyValue;
    private String description;
}

