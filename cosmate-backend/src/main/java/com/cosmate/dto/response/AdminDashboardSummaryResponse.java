package com.cosmate.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardSummaryResponse {
    private long totalUsers;
    private long totalProviders;
    private long totalCostumes;
    private long totalOrders;
    private long openDisputes;
    private long pendingWithdrawRequests;
    private long reviewsToModerate;
    private BigDecimal revenueToday;
    private BigDecimal revenueThisMonth;
}
