package com.cosmate.controller;

import com.cosmate.dto.request.OrderExtendRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.OrderExtendResponse;
import com.cosmate.service.OrderExtendService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderExtendController {

    private final OrderExtendService orderExtendService;

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;
        Object principal = auth.getPrincipal();
        try { return Integer.valueOf(principal.toString()); } catch (Exception e) { return null; }
    }

    @PostMapping("/{orderId}/details/{detailId}/extend")
    public ApiResponse<OrderExtendResponse> requestExtend(@PathVariable Integer orderId,
                                                          @PathVariable Integer detailId,
                                                          @Valid @RequestBody OrderExtendRequest req) {
        try {
            Integer userId = getCurrentUserId();
            if (userId == null) return ApiResponse.<OrderExtendResponse>builder().code(401).message("Unauthorized").build();
            OrderExtendResponse resp = orderExtendService.requestExtend(userId, orderId, detailId, req);
            return ApiResponse.<OrderExtendResponse>builder().result(resp).message("Extend requested").build();
        } catch (IllegalArgumentException ex) {
            return ApiResponse.<OrderExtendResponse>builder().code(400).message(ex.getMessage()).build();
        } catch (Exception ex) {
            return ApiResponse.<OrderExtendResponse>builder().code(500).message("Failed to request extend: " + ex.getMessage()).build();
        }
    }

    @PostMapping("/{orderId}/details/{detailId}/extend/{extendId}/pay")
    public ApiResponse<OrderExtendResponse> payExtend(@PathVariable Integer orderId,
                                                      @PathVariable Integer detailId,
                                                      @PathVariable Integer extendId,
                                                      @RequestParam(required = false) String paymentMethod,
                                                      @RequestParam(required = false) String returnUrl) {
        try {
            Integer userId = getCurrentUserId();
            if (userId == null) return ApiResponse.<OrderExtendResponse>builder().code(401).message("Unauthorized").build();
            OrderExtendResponse resp = orderExtendService.payExtend(userId, orderId, extendId, paymentMethod, returnUrl);
            return ApiResponse.<OrderExtendResponse>builder().result(resp).message("Extend paid").build();
        } catch (IllegalArgumentException ex) {
            return ApiResponse.<OrderExtendResponse>builder().code(400).message(ex.getMessage()).build();
        } catch (Exception ex) {
            return ApiResponse.<OrderExtendResponse>builder().code(500).message("Failed to pay extend: " + ex.getMessage()).build();
        }
    }

    @DeleteMapping("/{orderId}/details/{detailId}/extend/{extendId}")
    public ApiResponse<OrderExtendResponse> cancelExtend(@PathVariable Integer orderId,
                                                         @PathVariable Integer detailId,
                                                         @PathVariable Integer extendId) {
        try {
            Integer userId = getCurrentUserId();
            if (userId == null) return ApiResponse.<OrderExtendResponse>builder().code(401).message("Unauthorized").build();
            OrderExtendResponse resp = orderExtendService.cancelExtend(userId, orderId, extendId);
            return ApiResponse.<OrderExtendResponse>builder().result(resp).message("Extend cancelled").build();
        } catch (IllegalArgumentException ex) {
            return ApiResponse.<OrderExtendResponse>builder().code(400).message(ex.getMessage()).build();
        } catch (Exception ex) {
            return ApiResponse.<OrderExtendResponse>builder().code(500).message("Failed to cancel extend: " + ex.getMessage()).build();
        }
    }

    @GetMapping("/{orderId}/details/{detailId}/extend/{extendId}")
    public ApiResponse<OrderExtendResponse> getExtendById(@PathVariable Integer orderId,
                                                          @PathVariable Integer detailId,
                                                          @PathVariable Integer extendId) {
        try {
            Integer userId = getCurrentUserId();
            if (userId == null) return ApiResponse.<OrderExtendResponse>builder().code(401).message("Unauthorized").build();
            OrderExtendResponse resp = orderExtendService.getExtendById(userId, orderId, extendId);
            return ApiResponse.<OrderExtendResponse>builder().result(resp).message("Extend fetched").build();
        } catch (IllegalArgumentException ex) {
            return ApiResponse.<OrderExtendResponse>builder().code(400).message(ex.getMessage()).build();
        } catch (Exception ex) {
            return ApiResponse.<OrderExtendResponse>builder().code(500).message("Failed to fetch extend: " + ex.getMessage()).build();
        }
    }

    @GetMapping("/{orderId}/extends")
    public ApiResponse<java.util.List<OrderExtendResponse>> getExtendsByOrder(@PathVariable Integer orderId) {
        try {
            Integer userId = getCurrentUserId();
            if (userId == null) return ApiResponse.<java.util.List<OrderExtendResponse>>builder().code(401).message("Unauthorized").build();
            java.util.List<OrderExtendResponse> resp = orderExtendService.getExtendsByOrder(userId, orderId);
            return ApiResponse.<java.util.List<OrderExtendResponse>>builder().result(resp).message("Extends fetched").build();
        } catch (IllegalArgumentException ex) {
            return ApiResponse.<java.util.List<OrderExtendResponse>>builder().code(400).message(ex.getMessage()).build();
        } catch (Exception ex) {
            return ApiResponse.<java.util.List<OrderExtendResponse>>builder().code(500).message("Failed to fetch extends: " + ex.getMessage()).build();
        }
    }
}


