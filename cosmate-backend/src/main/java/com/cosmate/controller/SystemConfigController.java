package com.cosmate.controller;

import com.cosmate.dto.request.system.SystemConfigUpdateRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.entity.SystemConfig;
import com.cosmate.service.SystemConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/system-config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<SystemConfig>> getAllConfigs() {
        return ApiResponse.<List<SystemConfig>>builder()
                .result(systemConfigService.getAllConfigs())
                .message("Fetched system configs successfully")
                .build();
    }

    @PutMapping("/{configKey}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SystemConfig> updateConfig(
            @PathVariable String configKey,
            @Valid @RequestBody SystemConfigUpdateRequest request) {
        return ApiResponse.<SystemConfig>builder()
                .result(systemConfigService.updateConfig(configKey, request))
                .message("Updated system config successfully")
                .build();
    }
}
