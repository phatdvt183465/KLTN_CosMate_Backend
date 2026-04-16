package com.cosmate.controller;

import com.cosmate.dto.response.AdminAuditLogResponse;
import com.cosmate.dto.response.AdminDashboardSummaryResponse;
import com.cosmate.dto.response.AdminReportSeriesPointResponse;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/dashboard/summary")
    public ApiResponse<AdminDashboardSummaryResponse> summary() {
        return ApiResponse.<AdminDashboardSummaryResponse>builder()
                .code(0)
                .message("OK")
                .result(adminDashboardService.getSummary())
                .build();
    }

    @GetMapping("/reports/revenue")
    public ApiResponse<List<AdminReportSeriesPointResponse>> revenue() {
        return ApiResponse.<List<AdminReportSeriesPointResponse>>builder()
                .code(0)
                .message("OK")
                .result(adminDashboardService.getRevenueReport())
                .build();
    }

    @GetMapping("/reports/orders")
    public ApiResponse<List<AdminReportSeriesPointResponse>> orders() {
        return ApiResponse.<List<AdminReportSeriesPointResponse>>builder()
                .code(0)
                .message("OK")
                .result(adminDashboardService.getOrdersReport())
                .build();
    }

    @GetMapping("/reports/users")
    public ApiResponse<List<AdminReportSeriesPointResponse>> users() {
        return ApiResponse.<List<AdminReportSeriesPointResponse>>builder()
                .code(0)
                .message("OK")
                .result(adminDashboardService.getUsersReport())
                .build();
    }

    @GetMapping("/reports/providers")
    public ApiResponse<List<AdminReportSeriesPointResponse>> providers() {
        return ApiResponse.<List<AdminReportSeriesPointResponse>>builder()
                .code(0)
                .message("OK")
                .result(adminDashboardService.getProvidersReport())
                .build();
    }

    @GetMapping("/reports/disputes")
    public ApiResponse<List<AdminReportSeriesPointResponse>> disputes() {
        return ApiResponse.<List<AdminReportSeriesPointResponse>>builder()
                .code(0)
                .message("OK")
                .result(adminDashboardService.getDisputesReport())
                .build();
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<AdminAuditLogResponse>> auditLogs() {
        return ApiResponse.<List<AdminAuditLogResponse>>builder()
                .code(0)
                .message("OK")
                .result(adminDashboardService.getAuditLogs())
                .build();
    }
}
