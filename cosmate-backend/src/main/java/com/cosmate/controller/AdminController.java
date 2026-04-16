package com.cosmate.controller;

import com.cosmate.dto.response.AdminAuditLogResponse;
import com.cosmate.dto.response.AdminDashboardSummaryResponse;
import com.cosmate.dto.response.AdminReportSeriesPointResponse;
import com.cosmate.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/dashboard/summary")
    public ResponseEntity<AdminDashboardSummaryResponse> getDashboardSummary() {
        return ResponseEntity.ok(adminDashboardService.getSummary());
    }

    @GetMapping("/reports/revenue")
    public ResponseEntity<List<AdminReportSeriesPointResponse>> getRevenueReport() {
        return ResponseEntity.ok(adminDashboardService.getRevenueReport());
    }

    @GetMapping("/reports/orders")
    public ResponseEntity<List<AdminReportSeriesPointResponse>> getOrdersReport() {
        return ResponseEntity.ok(adminDashboardService.getOrdersReport());
    }

    @GetMapping("/reports/users")
    public ResponseEntity<List<AdminReportSeriesPointResponse>> getUsersReport() {
        return ResponseEntity.ok(adminDashboardService.getUsersReport());
    }

    @GetMapping("/reports/providers")
    public ResponseEntity<List<AdminReportSeriesPointResponse>> getProvidersReport() {
        return ResponseEntity.ok(adminDashboardService.getProvidersReport());
    }

    @GetMapping("/reports/disputes")
    public ResponseEntity<List<AdminReportSeriesPointResponse>> getDisputesReport() {
        return ResponseEntity.ok(adminDashboardService.getDisputesReport());
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AdminAuditLogResponse>> getAuditLogs() {
        return ResponseEntity.ok(adminDashboardService.getAuditLogs());
    }
}
