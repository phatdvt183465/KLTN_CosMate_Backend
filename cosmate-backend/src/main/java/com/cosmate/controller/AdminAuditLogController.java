package com.cosmate.controller;

import com.cosmate.dto.response.AdminAuditLogResponse;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.service.AdminAuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminAuditLogController {
    private final AdminAuditLogService service;

    @GetMapping("/audit-logs")
    public ApiResponse<List<AdminAuditLogResponse>> auditLogs() {
        return ApiResponse.<List<AdminAuditLogResponse>>builder()
                .code(0)
                .message("OK")
                .result(service.findAll())
                .build();
    }
}
