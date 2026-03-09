package com.cosmate.controller;

import com.cosmate.dto.request.UpdateProviderRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.ProviderPublicResponse;
import com.cosmate.dto.response.ProviderResponse;
import com.cosmate.entity.Provider;
import com.cosmate.exception.AppException;
import com.cosmate.exception.ErrorCode;
import com.cosmate.service.ProviderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;
    private final com.cosmate.service.FirebaseStorageService firebaseStorageService;
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

    private boolean isPrivilegedViewer(Provider p) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        Integer currentUserId = getCurrentUserId();
        // owner (provider user) can view
        if (currentUserId != null && currentUserId.equals(p.getUserId())) {
            // if owner has a provider-type role we allow; but we can't check roles on User here easily.
            // We'll allow owner access regardless of role to be safe (owner sees own bank info).
            return true;
        }
        // staff/admin/superadmin can view
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_STAFF".equals(a.getAuthority()) || "ROLE_SUPERADMIN".equals(a.getAuthority()));
    }

    // Public: list all providers (anyone can call)
    @GetMapping("")
    public ResponseEntity<ApiResponse<List<ProviderPublicResponse>>> listProviders() {
        ApiResponse<List<ProviderPublicResponse>> api = new ApiResponse<>();
        try {
            List<Provider> providers = providerService.listAllProviders();
            List<ProviderPublicResponse> resp = providers.stream().map(this::toPublicResponse).collect(Collectors.toList());
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(resp);
            return ResponseEntity.ok(api);
        } catch (Exception e) {
            log.error("Unexpected error listing providers: {}", e.getMessage(), e);
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + e.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    // Public: get provider by provider id
    @GetMapping("/id/{providerId}")
    public ResponseEntity<ApiResponse<ProviderResponse>> getByProviderId(@PathVariable("providerId") Integer providerId) {
        ApiResponse<ProviderResponse> api = new ApiResponse<>();
        try {
            Provider p = providerService.getById(providerId);
            ProviderResponse resp = toResponse(p);
            if (!isPrivilegedViewer(p)) {
                // hide bank info for non-privileged viewers
                resp.setBankAccountNumber(null);
                resp.setBankName(null);
            }
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(resp);
            return ResponseEntity.ok(api);
        } catch (AppException ae) {
            ErrorCode ec = ae.getErrorCode();
            api.setCode(ec.getCode());
            api.setMessage(ec.getMessage());
            if (ec == ErrorCode.PROVIDER_NOT_FOUND) return ResponseEntity.status(404).body(api);
            return ResponseEntity.badRequest().body(api);
        } catch (Exception e) {
            log.error("Unexpected error fetching provider by id {}: {}", providerId, e.getMessage(), e);
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + e.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    // Public: get provider by user id
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<ProviderResponse>> getByUserId(@PathVariable("userId") Integer userId) {
        ApiResponse<ProviderResponse> api = new ApiResponse<>();
        try {
            Provider p = providerService.getByUserId(userId);
            ProviderResponse resp = toResponse(p);
            if (!isPrivilegedViewer(p)) {
                resp.setBankAccountNumber(null);
                resp.setBankName(null);
            }
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(resp);
            return ResponseEntity.ok(api);
        } catch (AppException ae) {
            ErrorCode ec = ae.getErrorCode();
            api.setCode(ec.getCode());
            api.setMessage(ec.getMessage());
            if (ec == ErrorCode.PROVIDER_NOT_FOUND) return ResponseEntity.status(404).body(api);
            return ResponseEntity.badRequest().body(api);
        } catch (Exception e) {
            log.error("Unexpected error fetching provider by user {}: {}", userId, e.getMessage(), e);
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
            allowed = authorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_STAFF".equals(a.getAuthority()) || "ROLE_SUPERADMIN".equals(a.getAuthority()));
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

    @PutMapping(value = "/{id}/cover-image", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<ApiResponse<ProviderResponse>> updateCoverImage(@PathVariable("id") Integer id,
                                                                          @RequestPart(value = "coverImage") MultipartFile coverImage) {
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
            // upload file to firebase
            String filename = String.format("providers/%d/cover_%d", id, System.currentTimeMillis());
            // preserve extension if possible
            String original = coverImage.getOriginalFilename();
            if (original != null && original.contains(".")) filename += original.substring(original.lastIndexOf('.'));
            String url = firebaseStorageService.uploadFile(coverImage, filename);

            Provider p = providerService.updateCoverImageForUser(id, url);
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(toResponse(p));
            return ResponseEntity.ok(api);
        } catch (com.cosmate.exception.AppException ae) {
            ErrorCode ec = ae.getErrorCode();
            api.setCode(ec.getCode());
            api.setMessage(ec.getMessage());
            if (ec == ErrorCode.FORBIDDEN || ec == ErrorCode.ACCOUNT_BANNED) return ResponseEntity.status(403).body(api);
            return ResponseEntity.badRequest().body(api);
        } catch (Exception e) {
            log.error("Unexpected error updating provider cover image for user {}: {}", id, e.getMessage(), e);
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + e.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    private ProviderPublicResponse toPublicResponse(Provider p) {
        return ProviderPublicResponse.builder()
                .id(p.getId())
                .userId(p.getUserId())
                .shopName(p.getShopName())
                .shopAddressId(p.getShopAddressId())
                .avatarUrl(p.getAvatarUrl())
                .coverImageUrl(p.getCoverImageUrl())
                .bio(p.getBio())
                .verified(p.getVerified())
                .completedOrders(p.getCompletedOrders())
                .totalRating(p.getTotalRating())
                .totalReviews(p.getTotalReviews())
                .build();
    }

    private ProviderResponse toResponse(Provider p) {
        return ProviderResponse.builder()
                .id(p.getId())
                .userId(p.getUserId())
                .shopName(p.getShopName())
                .shopAddressId(p.getShopAddressId())
                .avatarUrl(p.getAvatarUrl())
                .coverImageUrl(p.getCoverImageUrl())
                .bio(p.getBio())
                .bankAccountNumber(p.getBankAccountNumber())
                .bankName(p.getBankName())
                .verified(p.getVerified())
                .completedOrders(p.getCompletedOrders())
                .totalRating(p.getTotalRating())
                .totalReviews(p.getTotalReviews())
                .build();
    }
}
