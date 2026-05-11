package com.cosmate.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderStatisticsResponse {
    private Long totalCostumes;
    private Long totalOrders;
    private Long totalOrderItems;
    private Long completedOrders;
    private BigDecimal totalRevenue;
    private List<AdminReportSeriesPointResponse> revenueByMonth;
    private List<AdminReportSeriesPointResponse> revenueByQuarter;
}

