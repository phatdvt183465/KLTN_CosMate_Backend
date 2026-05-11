package com.cosmate.service;

import com.cosmate.dto.response.ProviderStatisticsResponse;

public interface ProviderStatisticsService {
    ProviderStatisticsResponse getProviderStatistics(Integer providerId, Integer months);
    java.util.List<com.cosmate.dto.response.OrderStatusCountResponse> getOrderCountsByStatus(Integer providerId);
    java.util.List<com.cosmate.dto.response.TransactionResponse> getRecentWalletTransactions(Integer providerId, Integer limit);
}

