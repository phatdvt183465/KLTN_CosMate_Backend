package com.cosmate.controller;

import com.cosmate.dto.request.ProviderSubscribeRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.ProviderSubscriptionResponse;
import com.cosmate.entity.ProviderSubscription;
import com.cosmate.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/providers/subscriptions")
@RequiredArgsConstructor
public class ProviderSubscriptionController {

    private final SubscriptionService subscriptionService;
    private static final Logger log = LoggerFactory.getLogger(ProviderSubscriptionController.class);

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

    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse<String>> subscribe(@RequestBody ProviderSubscribeRequest req) {
        ApiResponse<String> api = new ApiResponse<>();
        Integer currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            api.setCode(1001);
            api.setMessage("Chưa xác thực - Vui lòng đăng nhập");
            return ResponseEntity.status(401).body(api);
        }

        try {
            String url = subscriptionService.initiateProviderSubscription(currentUserId, req.getSubscriptionPlanId(), req.getReturnUrl());
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(url);
            return ResponseEntity.ok(api);
        } catch (Exception e) {
            log.error("Failed to initiate subscription for user {}: {}", currentUserId, e.getMessage(), e);
            api.setCode(9999);
            api.setMessage(e.getMessage());
            return ResponseEntity.internalServerError().body(api);
        }
    }

    // New: provider can list their own subscriptions
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<List<ProviderSubscriptionResponse>>> listMySubscriptions(@PathVariable Integer id) {
        ApiResponse<List<ProviderSubscriptionResponse>> api = new ApiResponse<>();
        Integer currentUserId = getCurrentUserId();
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
            List<ProviderSubscription> subs = subscriptionService.listByProviderUserId(currentUserId);
            List<ProviderSubscriptionResponse> resp = subs.stream().map(s -> ProviderSubscriptionResponse.builder()
                    .id(s.getId())
                    .providerId(s.getProvider() != null ? s.getProvider().getId() : null)
                    .subscriptionPlanId(s.getSubscriptionPlan() != null ? s.getSubscriptionPlan().getId() : null)
                    .name(s.getName())
                    .duration(s.getDuration())
                    .price(s.getPrice())
                    .startDate(s.getStartDate())
                    .endDate(s.getEndDate())
                    .status(s.getStatus())
                    .transactionId(s.getTransaction() != null ? s.getTransaction().getId() : null)
                    .createdAt(s.getCreatedAt())
                    .build()).collect(Collectors.toList());

            api.setCode(0);
            api.setMessage("OK");
            api.setResult(resp);
            return ResponseEntity.ok(api);
        } catch (Exception e) {
            log.error("Failed to list subscriptions for user {}: {}", currentUserId, e.getMessage(), e);
            api.setCode(9999);
            api.setMessage(e.getMessage());
            return ResponseEntity.internalServerError().body(api);
        }
    }

    // New: ADMIN/STAFF can list all subscriptions
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<ProviderSubscriptionResponse>>> listAllSubscriptions() {
        ApiResponse<List<ProviderSubscriptionResponse>> api = new ApiResponse<>();
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
            List<ProviderSubscription> subs = subscriptionService.listAllSubscriptions();
            List<ProviderSubscriptionResponse> resp = subs.stream().map(s -> ProviderSubscriptionResponse.builder()
                    .id(s.getId())
                    .providerId(s.getProvider() != null ? s.getProvider().getId() : null)
                    .subscriptionPlanId(s.getSubscriptionPlan() != null ? s.getSubscriptionPlan().getId() : null)
                    .name(s.getName())
                    .duration(s.getDuration())
                    .price(s.getPrice())
                    .startDate(s.getStartDate())
                    .endDate(s.getEndDate())
                    .status(s.getStatus())
                    .transactionId(s.getTransaction() != null ? s.getTransaction().getId() : null)
                    .createdAt(s.getCreatedAt())
                    .build()).collect(Collectors.toList());

            api.setCode(0);
            api.setMessage("OK");
            api.setResult(resp);
            return ResponseEntity.ok(api);
        } catch (Exception e) {
            log.error("Failed to list all subscriptions: {}", e.getMessage(), e);
            api.setCode(9999);
            api.setMessage(e.getMessage());
            return ResponseEntity.internalServerError().body(api);
        }
    }
}
