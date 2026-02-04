package com.cosmate.controller;

import com.cosmate.dto.request.UpdateProviderRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.ProviderResponse;
import com.cosmate.entity.Provider;
import com.cosmate.exception.AppException;
import com.cosmate.exception.ErrorCode;
import com.cosmate.service.ProviderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;
    private static final Logger log = LoggerFactory.getLogger(ProviderController.class);

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;
        Object principal = auth.getPrincipal();
        try {
            if (principal instanceof String) {
                String s = (String) principal;
                if (s.equalsIgnoreCase("anonymousUser")) return null;
                return Integer.valueOf(s);
            }
            if (principal instanceof Integer) return (Integer) principal;
            if (principal instanceof Long) return ((Long) principal).intValue();
            return Integer.valueOf(principal.toString());
        } catch (Exception e) {
            log.debug("Unable to parse current principal to userId: {}", principal);
            return null;
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProviderResponse>> createForCurrentUser() {
        Integer currentUserId = getCurrentUserId();
        ApiResponse<ProviderResponse> api = new ApiResponse<>();
        if (currentUserId == null) {
            api.setCode(1001);
            api.setMessage("Chưa xác thực - Vui lòng đăng nhập");
            return ResponseEntity.status(401).body(api);
        }
        try {
            Provider p = providerService.createForUser(currentUserId);
            ProviderResponse resp = toResponse(p);
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(resp);
            return ResponseEntity.ok(api);
        } catch (AppException ae) {
            ErrorCode ec = ae.getErrorCode();
            api.setCode(ec.getCode());
            api.setMessage(ec.getMessage());
            if (ec == ErrorCode.FORBIDDEN || ec == ErrorCode.ACCOUNT_BANNED) return ResponseEntity.status(403).body(api);
            return ResponseEntity.badRequest().body(api);
        } catch (Exception e) {
            log.error("Unexpected error creating provider for user {}: {}", currentUserId, e.getMessage(), e);
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + e.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProviderResponse>> getProvider(@PathVariable Integer id) {
        Integer currentUserId = getCurrentUserId();
        ApiResponse<ProviderResponse> api = new ApiResponse<>();
        if (currentUserId == null) {
            api.setCode(1001);
            api.setMessage("Chưa xác thực - Vui lòng đăng nhập");
            return ResponseEntity.status(401).body(api);
        }
        if (!currentUserId.equals(id)) {
            api.setCode(1006);
            api.setMessage("Không có quyền thực hiện thao tác này!");
            return ResponseEntity.status(403).body(api);
        }
        try {
            Provider p = providerService.getByUserId(currentUserId);
            ProviderResponse resp = toResponse(p);
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(resp);
            return ResponseEntity.ok(api);
        } catch (AppException ae) {
            ErrorCode ec = ae.getErrorCode();
            api.setCode(ec.getCode());
            api.setMessage(ec.getMessage());
            if (ec == ErrorCode.FORBIDDEN || ec == ErrorCode.ACCOUNT_BANNED) return ResponseEntity.status(403).body(api);
            return ResponseEntity.badRequest().body(api);
        } catch (Exception e) {
            log.error("Unexpected error fetching provider for user {}: {}", currentUserId, e.getMessage(), e);
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + e.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProviderResponse>> updateProvider(@PathVariable Integer id, @RequestBody UpdateProviderRequest request) {
        Integer currentUserId = getCurrentUserId();
        ApiResponse<ProviderResponse> api = new ApiResponse<>();
        if (currentUserId == null) {
            api.setCode(1001);
            api.setMessage("Chưa xác thực - Vui lòng đăng nhập");
            return ResponseEntity.status(401).body(api);
        }
        if (!currentUserId.equals(id)) {
            api.setCode(1006);
            api.setMessage("Không có quyền thực hiện thao tác này!");
            return ResponseEntity.status(403).body(api);
        }
        try {
            Provider p = providerService.updateOwnProvider(currentUserId, request);
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(toResponse(p));
            return ResponseEntity.ok(api);
        } catch (AppException ae) {
            ErrorCode ec = ae.getErrorCode();
            api.setCode(ec.getCode());
            api.setMessage(ec.getMessage());
            if (ec == ErrorCode.FORBIDDEN || ec == ErrorCode.ACCOUNT_BANNED) return ResponseEntity.status(403).body(api);
            return ResponseEntity.badRequest().body(api);
        } catch (Exception e) {
            log.error("Unexpected error updating provider for user {}: {}", currentUserId, e.getMessage(), e);
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + e.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    @PatchMapping("/{id}/verify")
    public ResponseEntity<ApiResponse<ProviderResponse>> setVerified(@PathVariable Integer id, @RequestParam boolean verified) {
        ApiResponse<ProviderResponse> api = new ApiResponse<>();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = false;
        if (auth != null && auth.isAuthenticated()) {
            var authorities = auth.getAuthorities();
            allowed = authorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_STAFF".equals(a.getAuthority()));
        }
        if (!allowed) {
            api.setCode(1006);
            api.setMessage("Không có quyền thực hiện thao tác này!");
            return ResponseEntity.status(403).body(api);
        }

        try {
            Provider p = providerService.setVerified(id, verified);
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(toResponse(p));
            return ResponseEntity.ok(api);
        } catch (AppException ae) {
            ErrorCode ec = ae.getErrorCode();
            api.setCode(ec.getCode());
            api.setMessage(ec.getMessage());
            if (ec == ErrorCode.FORBIDDEN || ec == ErrorCode.ACCOUNT_BANNED) return ResponseEntity.status(403).body(api);
            return ResponseEntity.badRequest().body(api);
        } catch (Exception e) {
            log.error("Unexpected error setting verified for provider {}: {}", id, e.getMessage(), e);
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + e.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    private ProviderResponse toResponse(Provider p) {
        return ProviderResponse.builder()
                .id(p.getId())
                .userId(p.getUserId())
                .shopName(p.getShopName())
                .shopAddressId(p.getShopAddressId())
                .avatarUrl(p.getAvatarUrl())
                .bio(p.getBio())
                .bankAccountNumber(p.getBankAccountNumber())
                .bankName(p.getBankName())
                .verified(p.getVerified())
                .build();
    }
}
