package com.cosmate.service;

import com.cosmate.dto.response.ProviderStatisticsResponse;

public interface ProviderStatisticsService {
    ProviderStatisticsResponse getProviderStatistics(Integer providerId, Integer months);
}

