package com.cosmate.service;

import com.cosmate.dto.response.AdminAuditLogResponse;
import com.cosmate.dto.response.AdminDashboardSummaryResponse;
import com.cosmate.dto.response.AdminReportSeriesPointResponse;

import java.util.List;

public interface AdminDashboardService {
    AdminDashboardSummaryResponse getSummary();
    List<AdminReportSeriesPointResponse> getRevenueReport();
    List<AdminReportSeriesPointResponse> getOrdersReport();
    List<AdminReportSeriesPointResponse> getUsersReport();
    List<AdminReportSeriesPointResponse> getProvidersReport();
    List<AdminReportSeriesPointResponse> getDisputesReport();
    List<AdminAuditLogResponse> getAuditLogs();
}
