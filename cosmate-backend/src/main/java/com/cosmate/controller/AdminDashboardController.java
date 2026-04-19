package com.cosmate.controller;

import com.cosmate.dto.response.AdminDashboardSummaryResponse;
import com.cosmate.dto.response.AdminReportSeriesPointResponse;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.service.AdminDashboardService;
import com.cosmate.service.CharacterSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final CharacterSyncService characterSyncService;

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

    @PostMapping("/sync-characters")
    public ApiResponse<Integer> syncCharacters() {
        try {
            int added = characterSyncService.syncTopCharacters();
            return ApiResponse.<Integer>builder()
                    .code(0)
                    .message("Đã đồng bộ thành công " + added + " nhân vật")
                    .result(added)
                    .build();
        } catch (Exception e) {
            return ApiResponse.<Integer>builder()
                    .code(1)
                    .message(e.getMessage())
                    .build();
        }
    }

}
