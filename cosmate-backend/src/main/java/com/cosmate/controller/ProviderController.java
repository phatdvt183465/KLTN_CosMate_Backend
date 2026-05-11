package com.cosmate.controller;

import com.cosmate.dto.request.UpdateProviderRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.ProviderPublicResponse;
import com.cosmate.dto.response.ProviderResponse;
import com.cosmate.entity.Provider;
import com.cosmate.exception.AppException;
import com.cosmate.exception.ErrorCode;
import com.cosmate.service.ProviderService;
import com.cosmate.service.ProviderStatisticsService;
import com.cosmate.service.SubscriptionService;
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
    private final ProviderStatisticsService providerStatisticsService;
    private final SubscriptionService subscriptionService;
    
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
            List<ProviderPublicResponse> resp = providerService.listAllProvidersPublic();
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
            // fetch provider to decide bank info visibility
            Provider p = providerService.getById(providerId);
            boolean includeBank = isPrivilegedViewer(p);
            ProviderResponse resp = providerService.getResponseByProviderId(providerId, includeBank);
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
            boolean includeBank = isPrivilegedViewer(p);
            ProviderResponse resp = providerService.getResponseByUserId(userId, includeBank);
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
        try {
            // --- ĐOẠN SỬA MỚI ---
            // Tìm Provider theo id (providerId) truyền từ URL
            Provider p = providerService.getById(id);

            // Kiểm tra xem user đang đăng nhập có phải là chủ của provider này không
            if (!currentUserId.equals(p.getUserId())) {
                api.setCode(1006);
                api.setMessage("Không có quyền thực hiện thao tác này!");
                return ResponseEntity.status(403).body(api);
            }

            // Đã check đúng chủ, tiến hành update (hàm này đang nhận currentUserId)
            providerService.updateOwnProvider(currentUserId, request);
            ProviderResponse resp = providerService.getResponseByUserId(currentUserId, true);
            // --- KẾT THÚC ĐOẠN SỬA ---

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
            providerService.setVerified(id, verified);
            // admin/staff may view full info
            ProviderResponse resp = providerService.getResponseByProviderId(id, true);
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
        try {
            // --- ĐOẠN SỬA MỚI ---
            // Tìm Provider theo id (providerId)
            Provider p = providerService.getById(id);

            if (!currentUserId.equals(p.getUserId())) {
                api.setCode(1006);
                api.setMessage("Không có quyền thực hiện thao tác này!");
                return ResponseEntity.status(403).body(api);
            }

            // Truyền currentUserId vào service như cũ
            ProviderResponse resp = providerService.updateCoverImageForUserUpload(currentUserId, coverImage);
            // --- KẾT THÚC ĐOẠN SỬA ---

            api.setCode(0);
            api.setMessage("OK");
            api.setResult(resp);
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

    @GetMapping("/role/{roleName}")
    public ApiResponse<List<ProviderResponse>> getProvidersByRole(@PathVariable String roleName) {
        return ApiResponse.<List<ProviderResponse>>builder()
                .result(providerService.getProvidersByRole(roleName))
                .message("Lấy danh sách Provider theo vai trò thành công")
                .build();
    }

    @GetMapping("/{id}/statistics")
    public ResponseEntity<ApiResponse<com.cosmate.dto.response.ProviderStatisticsResponse>> getProviderStatistics(@PathVariable("id") Integer id,
                                                                                                                  @RequestParam(value = "months", required = false) Integer months) {
        ApiResponse<com.cosmate.dto.response.ProviderStatisticsResponse> api = new ApiResponse<>();
        try {
            Provider p = providerService.getById(id);
            if (!isPrivilegedViewer(p)) {
                api.setCode(1006);
                api.setMessage("Không có quyền truy cập thống kê provider này");
                return ResponseEntity.status(403).body(api);
            }

            com.cosmate.dto.response.ProviderStatisticsResponse resp = providerStatisticsService.getProviderStatistics(id, months);
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
            log.error("Unexpected error fetching provider statistics for {}: {}", id, e.getMessage(), e);
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + e.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    @GetMapping("/{id}/orders-status")
    public ResponseEntity<ApiResponse<java.util.List<com.cosmate.dto.response.OrderStatusCountResponse>>> getOrdersByStatus(@PathVariable("id") Integer id) {
        ApiResponse<java.util.List<com.cosmate.dto.response.OrderStatusCountResponse>> api = new ApiResponse<>();
        try {
            Provider p = providerService.getById(id);
            if (!isPrivilegedViewer(p)) {
                api.setCode(1006);
                api.setMessage("Không có quyền truy cập thống kê provider này");
                return ResponseEntity.status(403).body(api);
            }
            var resp = providerStatisticsService.getOrderCountsByStatus(id);
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
            log.error("Unexpected error fetching orders by status for provider {}: {}", id, e.getMessage(), e);
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + e.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    @GetMapping("/{id}/wallet/transactions")
    public ResponseEntity<ApiResponse<java.util.List<com.cosmate.dto.response.TransactionResponse>>> getWalletTransactions(@PathVariable("id") Integer id,
                                                                                                                             @RequestParam(value = "limit", required = false) Integer limit) {
        ApiResponse<java.util.List<com.cosmate.dto.response.TransactionResponse>> api = new ApiResponse<>();
        try {
            Provider p = providerService.getById(id);
            if (!isPrivilegedViewer(p)) {
                api.setCode(1006);
                api.setMessage("Không có quyền truy cập thống kê provider này");
                return ResponseEntity.status(403).body(api);
            }
            var resp = providerStatisticsService.getRecentWalletTransactions(id, limit);
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
            log.error("Unexpected error fetching wallet transactions for provider {}: {}", id, e.getMessage(), e);
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + e.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    @GetMapping("/id/{providerId}/subscriptions-info")
    public ResponseEntity<ApiResponse<com.cosmate.dto.response.ProviderSubscriptionSummaryResponse>> getProviderSubscriptionsInfo(@PathVariable("providerId") Integer providerId) {
        ApiResponse<com.cosmate.dto.response.ProviderSubscriptionSummaryResponse> api = new ApiResponse<>();
        try {
            Provider p = providerService.getById(providerId);
            // Get provider subscriptions
            java.util.List<com.cosmate.entity.ProviderSubscription> subs = subscriptionService.listByProviderUserId(p.getUserId());
            java.time.LocalDateTime now = java.time.LocalDateTime.now();

            // Consider only ACTIVE subscriptions for remaining days
            java.util.List<com.cosmate.entity.ProviderSubscription> activeSubs = subs.stream()
                    .filter(s -> s.getStatus() != null && s.getStatus().equalsIgnoreCase("ACTIVE"))
                    .toList();

            long totalRemaining = activeSubs.stream().mapToLong(s -> {
                if (s.getEndDate() == null) return 0L;
                if (s.getEndDate().isAfter(now)) return java.time.Duration.between(now, s.getEndDate()).toDays();
                return 0L;
            }).sum();

            // Find current active subscription: one that contains now (start <= now <= end), otherwise pick the one with latest endDate
            java.util.Optional<com.cosmate.entity.ProviderSubscription> currentOpt = activeSubs.stream()
                    .filter(s -> s.getStartDate() != null && s.getEndDate() != null && ( !s.getStartDate().isAfter(now) && !s.getEndDate().isBefore(now) ))
                    .findFirst();
            if (currentOpt.isEmpty()) {
                currentOpt = activeSubs.stream().filter(s -> s.getEndDate() != null).sorted((a,b) -> b.getEndDate().compareTo(a.getEndDate())).findFirst();
            }

            String currentName = null;
            Long currentDays = 0L;
            if (currentOpt.isPresent()) {
                var s = currentOpt.get();
                currentName = s.getName();
                if (s.getEndDate() != null && s.getEndDate().isAfter(now)) {
                    currentDays = java.time.Duration.between(now, s.getEndDate()).toDays();
                    if (currentDays < 0) currentDays = 0L;
                } else {
                    currentDays = 0L;
                }
            }

            com.cosmate.dto.response.ProviderSubscriptionSummaryResponse resp = com.cosmate.dto.response.ProviderSubscriptionSummaryResponse.builder()
                    .currentPlanName(currentName)
                    .currentDaysRemaining(currentDays)
                    .totalRemainingDays(totalRemaining)
                    .build();

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
            log.error("Unexpected error fetching subscriptions info for provider {}: {}", providerId, e.getMessage(), e);
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + e.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }
}
