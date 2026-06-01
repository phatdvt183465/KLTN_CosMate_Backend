package com.cosmate.controller;

import com.cosmate.dto.request.CreateCancellationPolicyRequest;
import com.cosmate.dto.request.UpdateCancellationPolicyRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.ProviderCancellationPolicyResponse;
import com.cosmate.entity.ProviderCancellationPolicy;
import com.cosmate.exception.AppException;
import com.cosmate.exception.ErrorCode;
import com.cosmate.service.CancellationPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/providers/cancellation-policies")
@RequiredArgsConstructor
public class ProviderCancellationPolicyController {

    private final CancellationPolicyService policyService;

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<ApiResponse<List<ProviderCancellationPolicyResponse>>> listByProvider(@PathVariable Integer providerId) {
        ApiResponse<List<ProviderCancellationPolicyResponse>> api = new ApiResponse<>();
        try {
            var list = policyService.listByProvider(providerId);
            var resp = list.stream().map(p -> ProviderCancellationPolicyResponse.builder()
                    .id(p.getId())
                    .providerId(p.getProvider() == null ? null : p.getProvider().getId())
                    .minHoursBefore(p.getMinHoursBefore())
                    .maxHoursBefore(p.getMaxHoursBefore())
                    .penaltyType(p.getPenaltyType())
                    .penaltyValue(p.getPenaltyValue())
                    .description(p.getDescription())
                    .build()
            ).collect(Collectors.toList());

            api.setCode(0);
            api.setMessage("OK");
            api.setResult(resp);
            return ResponseEntity.ok(api);
        } catch (AppException ae) {
            ErrorCode ec = ae.getErrorCode();
            api.setCode(ec.getCode());
            api.setMessage(ec.getMessage());
            return ResponseEntity.badRequest().body(api);
        } catch (Exception e) {
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + e.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    @PostMapping("")
    public ResponseEntity<ApiResponse<ProviderCancellationPolicyResponse>> create(@RequestBody CreateCancellationPolicyRequest request) {
        ApiResponse<ProviderCancellationPolicyResponse> api = new ApiResponse<>();
        try {
            ProviderCancellationPolicy p = ProviderCancellationPolicy.builder()
                    .provider(com.cosmate.entity.Provider.builder().id(request.getProviderId()).build())
                    .minHoursBefore(request.getMinHoursBefore())
                    .maxHoursBefore(request.getMaxHoursBefore())
                    .penaltyType(request.getPenaltyType())
                    .penaltyValue(request.getPenaltyValue())
                    .description(request.getDescription())
                    .build();
            ProviderCancellationPolicy saved = policyService.create(p);
            ProviderCancellationPolicyResponse resp = ProviderCancellationPolicyResponse.builder()
                    .id(saved.getId())
                    .providerId(saved.getProvider() == null ? null : saved.getProvider().getId())
                    .minHoursBefore(saved.getMinHoursBefore())
                    .maxHoursBefore(saved.getMaxHoursBefore())
                    .penaltyType(saved.getPenaltyType())
                    .penaltyValue(saved.getPenaltyValue())
                    .description(saved.getDescription())
                    .build();
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(resp);
            return ResponseEntity.ok(api);
        } catch (AppException ae) {
            ErrorCode ec = ae.getErrorCode();
            api.setCode(ec.getCode());
            api.setMessage(ec.getMessage());
            return ResponseEntity.badRequest().body(api);
        } catch (Exception e) {
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + e.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        ApiResponse<Void> api = new ApiResponse<>();
        try {
            policyService.delete(id);
            api.setCode(0);
            api.setMessage("OK");
            return ResponseEntity.ok(api);
        } catch (AppException ae) {
            ErrorCode ec = ae.getErrorCode();
            api.setCode(ec.getCode());
            api.setMessage(ec.getMessage());
            return ResponseEntity.badRequest().body(api);
        } catch (Exception e) {
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + e.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProviderCancellationPolicyResponse>> update(@PathVariable Integer id, @RequestBody UpdateCancellationPolicyRequest request) {
        ApiResponse<ProviderCancellationPolicyResponse> api = new ApiResponse<>();
        try {
            ProviderCancellationPolicy p = ProviderCancellationPolicy.builder()
                    .id(id)
                    .penaltyType(request.getPenaltyType())
                    .penaltyValue(request.getPenaltyValue())
                    .description(request.getDescription())
                    .build();

            ProviderCancellationPolicy saved = policyService.update(p);
            ProviderCancellationPolicyResponse resp = ProviderCancellationPolicyResponse.builder()
                    .id(saved.getId())
                    .providerId(saved.getProvider() == null ? null : saved.getProvider().getId())
                    .minHoursBefore(saved.getMinHoursBefore())
                    .maxHoursBefore(saved.getMaxHoursBefore())
                    .penaltyType(saved.getPenaltyType())
                    .penaltyValue(saved.getPenaltyValue())
                    .description(saved.getDescription())
                    .build();

            api.setCode(0);
            api.setMessage("OK");
            api.setResult(resp);
            return ResponseEntity.ok(api);
        } catch (AppException ae) {
            ErrorCode ec = ae.getErrorCode();
            api.setCode(ec.getCode());
            api.setMessage(ec.getMessage());
            return ResponseEntity.badRequest().body(api);
        } catch (Exception e) {
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + e.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    @PostMapping("/seed-missing")
    public ResponseEntity<ApiResponse<Integer>> seedDefaultsForMissingProviders() {
        ApiResponse<Integer> api = new ApiResponse<>();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = false;
        if (auth != null && auth.isAuthenticated()) {
            allowed = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_SUPERADMIN".equals(a.getAuthority()));
        }
        if (!allowed) {
            api.setCode(1006);
            api.setMessage("Không có quyền thực hiện thao tác này!");
            return ResponseEntity.status(403).body(api);
        }

        try {
            int seeded = policyService.createDefaultsForProvidersMissingPolicies();
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(seeded);
            return ResponseEntity.ok(api);
        } catch (Exception e) {
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + e.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }
}
