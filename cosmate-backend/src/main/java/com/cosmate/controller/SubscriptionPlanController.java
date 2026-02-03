package com.cosmate.controller;

import com.cosmate.dto.request.SubscriptionPlanRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.SubscriptionPlanResponse;
import com.cosmate.entity.SubscriptionPlan;
import com.cosmate.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/subscription-plans")
@RequiredArgsConstructor
public class SubscriptionPlanController {

    private final SubscriptionService subscriptionService;

    private boolean isAdminOrStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_STAFF".equals(a.getAuthority()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SubscriptionPlanResponse>> createPlan(@RequestBody SubscriptionPlanRequest req) {
        ApiResponse<SubscriptionPlanResponse> api = new ApiResponse<>();
        if (!isAdminOrStaff()) {
            api.setCode(1006);
            api.setMessage("Không có quyền thực hiện thao tác này!");
            return ResponseEntity.status(403).body(api);
        }
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name(req.getName())
                .billingCycle(req.getBillingCycle())
                .cycleMonths(req.getCycleMonths())
                .price(req.getPrice())
                .isActive(req.getIsActive() == null ? true : req.getIsActive())
                .description(req.getDescription())
                .build();
        plan = subscriptionService.createPlan(plan);
        SubscriptionPlanResponse resp = SubscriptionPlanResponse.builder()
                .id(plan.getId())
                .name(plan.getName())
                .billingCycle(plan.getBillingCycle())
                .cycleMonths(plan.getCycleMonths())
                .price(plan.getPrice())
                .isActive(plan.getIsActive())
                .description(plan.getDescription())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(resp);
        return ResponseEntity.ok(api);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SubscriptionPlanResponse>> updatePlan(@PathVariable Integer id, @RequestBody SubscriptionPlanRequest req) {
        ApiResponse<SubscriptionPlanResponse> api = new ApiResponse<>();
        if (!isAdminOrStaff()) {
            api.setCode(1006);
            api.setMessage("Không có quyền thực hiện thao tác này!");
            return ResponseEntity.status(403).body(api);
        }
        SubscriptionPlan p = SubscriptionPlan.builder()
                .name(req.getName())
                .billingCycle(req.getBillingCycle())
                .cycleMonths(req.getCycleMonths())
                .price(req.getPrice())
                .isActive(req.getIsActive())
                .description(req.getDescription())
                .build();
        SubscriptionPlan updated = subscriptionService.updatePlan(id, p);
        SubscriptionPlanResponse resp = SubscriptionPlanResponse.builder()
                .id(updated.getId())
                .name(updated.getName())
                .billingCycle(updated.getBillingCycle())
                .cycleMonths(updated.getCycleMonths())
                .price(updated.getPrice())
                .isActive(updated.getIsActive())
                .description(updated.getDescription())
                .createdAt(updated.getCreatedAt())
                .updatedAt(updated.getUpdatedAt())
                .build();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(resp);
        return ResponseEntity.ok(api);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SubscriptionPlanResponse>>> listPlans() {
        ApiResponse<List<SubscriptionPlanResponse>> api = new ApiResponse<>();
        List<SubscriptionPlan> plans = subscriptionService.listPlans();
        List<SubscriptionPlanResponse> res = plans.stream().map(p -> SubscriptionPlanResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .billingCycle(p.getBillingCycle())
                .cycleMonths(p.getCycleMonths())
                .price(p.getPrice())
                .isActive(p.getIsActive())
                .description(p.getDescription())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build()).collect(Collectors.toList());
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(res);
        return ResponseEntity.ok(api);
    }
}
