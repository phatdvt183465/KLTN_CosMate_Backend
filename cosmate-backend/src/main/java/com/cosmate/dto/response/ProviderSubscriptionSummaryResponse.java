package com.cosmate.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderSubscriptionSummaryResponse {
    private String currentPlanName;
    private Long currentDaysRemaining;
    private Long totalRemainingDays;
}

