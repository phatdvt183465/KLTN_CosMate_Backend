package com.cosmate.controller;

import com.cosmate.dto.request.CreateServiceOrderRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.OrderResponse;
import com.cosmate.entity.*;
import com.cosmate.service.OrderService;
import com.cosmate.service.ProviderService;
// ...existing code...
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// ...existing code...

@RestController
@RequestMapping("/api/service-orders")
@RequiredArgsConstructor
public class ServiceOrderController {

    // Keep high-level services; delegate repository operations to OrderService implementation
    private final ProviderService providerService;
    private final OrderService orderService;
    private final com.cosmate.service.NotificationService notificationService;

    // helper to extract current authenticated user id
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
            return null;
        }
    }

    private boolean hasRole(Authentication auth, String roleName) {
        if (auth == null || auth.getAuthorities() == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> roleName.equals(a.getAuthority()) || roleName.equals(a.getAuthority().replace("ROLE_", "")));
    }

    // Provider creates a service booking for a cosplayer. Only providers of types PROVIDER_PHOTOGRAPH and PROVIDER_EVENT_STAFF allowed.
    @PostMapping("/provider-create")
    public ApiResponse<OrderResponse> providerCreateBooking(@RequestBody CreateServiceOrderRequest req) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<OrderResponse>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();

            // check provider ownership and roles in controller but delegate creation to service
            Provider prov = providerService.getByUserId(currentUserId);
            if (prov == null) return ApiResponse.<OrderResponse>builder().code(403).message("User is not a provider").build();
            boolean allowed = hasRole(auth, "PROVIDER_PHOTOGRAPH") || hasRole(auth, "PROVIDER_EVENT_STAFF");
            if (!allowed) return ApiResponse.<OrderResponse>builder().code(403).message("Only photography/event staff providers can create service bookings").build();

            OrderResponse resp = orderService.providerCreateBooking(currentUserId, req);
            return ApiResponse.<OrderResponse>builder().result(resp).message("Service booking created; waiting for cosplayer confirmation").build();
        } catch (IllegalArgumentException ex) {
            return ApiResponse.<OrderResponse>builder().code(400).message(ex.getMessage()).build();
        } catch (Exception ex) {
            return ApiResponse.<OrderResponse>builder().code(500).message("Failed to create service booking: " + ex.getMessage()).build();
        }
    }

    // Cosplayer confirms the service booking -> transition from UNCONFIRM to UNPAID (no payment performed here)
    @PostMapping("/{id}/confirm-by-cosplayer")
    public ApiResponse<OrderResponse> confirmByCosplayer(@PathVariable Integer id) {
        try {
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<OrderResponse>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();

            OrderResponse resp = orderService.confirmServiceOrderByCosplayer(currentUserId, id);
            return ApiResponse.<OrderResponse>builder().result(resp).message("Order confirmed by cosplayer and set to UNPAID").build();
        } catch (IllegalArgumentException ex) {
            return ApiResponse.<OrderResponse>builder().code(400).message(ex.getMessage()).build();
        } catch (Exception ex) {
            return ApiResponse.<OrderResponse>builder().code(500).message("Failed to confirm service order: " + ex.getMessage()).build();
        }
    }

    // Cosplayer pays an UNPAID service order -> delegates to OrderService.payOrder
    @PostMapping("/{id}/pay")
    public ApiResponse<OrderResponse> payServiceOrder(@PathVariable Integer id,
                                                      @RequestParam(required = false) String paymentMethod,
                                                      @RequestParam(required = false) String returnUrl) {
        try {
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<OrderResponse>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();

            // forward to the shared OrderService.payOrder logic (will validate ownership and UNPAID status)
            OrderResponse resp = orderService.payOrder(currentUserId, id, paymentMethod, returnUrl);
            return ApiResponse.<OrderResponse>builder().result(resp).message("Payment initiated").build();
        } catch (IllegalArgumentException ex) {
            return ApiResponse.<OrderResponse>builder().code(400).message(ex.getMessage()).build();
        } catch (Exception ex) {
            return ApiResponse.<OrderResponse>builder().code(500).message("Failed to process payment: " + ex.getMessage()).build();
        }
    }

    // Provider moves a PAID service order to WAITING_SERVICE_DATE (scheduling stage before service day)
    @PostMapping("/{id}/provider-set-waiting")
    public ApiResponse<OrderResponse> providerSetWaiting(@PathVariable Integer id) {
        try {
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<OrderResponse>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();
            OrderResponse resp = orderService.providerSetWaiting(currentUserId, id);
            return ApiResponse.<OrderResponse>builder().result(resp).message("Order set to WAITING_SERVICE_DATE").build();
        } catch (IllegalArgumentException ex) {
            return ApiResponse.<OrderResponse>builder().code(400).message(ex.getMessage()).build();
        } catch (Exception ex) {
            return ApiResponse.<OrderResponse>builder().code(500).message("Failed to set WAITING_SERVICE_DATE: " + ex.getMessage()).build();
        }
    }

    // Endpoint to manually trigger transition to IN_SERVICE for a given order (useful for testing or manual corrections)
    @PostMapping("/{id}/start-service-now")
    public ApiResponse<OrderResponse> startServiceNow(@PathVariable Integer id) {
        try {
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<OrderResponse>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();
            OrderResponse resp = orderService.startServiceNow(currentUserId, id);
            return ApiResponse.<OrderResponse>builder().result(resp).message("Order set to IN_SERVICE").build();
        } catch (IllegalArgumentException ex) {
            return ApiResponse.<OrderResponse>builder().code(400).message(ex.getMessage()).build();
        } catch (Exception ex) {
            return ApiResponse.<OrderResponse>builder().code(500).message("Failed to start service: " + ex.getMessage()).build();
        }
    }

    // Provider marks an IN_SERVICE order as COMPLETED
    @PostMapping("/{id}/provider-complete")
    public ApiResponse<OrderResponse> providerCompleteService(@PathVariable Integer id) {
        try {
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<OrderResponse>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();
            OrderResponse resp = orderService.providerCompleteService(currentUserId, id);
            return ApiResponse.<OrderResponse>builder().result(resp).message("Order marked as COMPLETED").build();
        } catch (IllegalArgumentException ex) {
            return ApiResponse.<OrderResponse>builder().code(400).message(ex.getMessage()).build();
        } catch (Exception ex) {
            return ApiResponse.<OrderResponse>builder().code(500).message("Failed to complete service order: " + ex.getMessage()).build();
        }
    }

    // Cancel order: if status is PAID then refund full amount to cosplayer's wallet + record transaction; if UNPAID or earlier then cancel without refund
    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancelOrder(@PathVariable Integer id) {
        try {
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<OrderResponse>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();
            OrderResponse resp = orderService.cancelOrder(currentUserId, id);
            return ApiResponse.<OrderResponse>builder().result(resp).message("Order cancelled").build();
        } catch (IllegalArgumentException ex) {
            return ApiResponse.<OrderResponse>builder().code(400).message(ex.getMessage()).build();
        } catch (Exception ex) {
            return ApiResponse.<OrderResponse>builder().code(500).message("Failed to cancel order: " + ex.getMessage()).build();
        }
    }

    // Provider: list service orders (RENT_SERVICE) belonging to provider, optional filter by comma-separated statuses
    @GetMapping("/provider")
    public ApiResponse<java.util.List<com.cosmate.dto.response.ServiceOrderItemResponse>> listProviderServiceOrders(@RequestParam(required = false) String statuses) {
        try {
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<java.util.List<com.cosmate.dto.response.ServiceOrderItemResponse>>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();
            java.util.List<com.cosmate.dto.response.ServiceOrderItemResponse> respList = orderService.listProviderServiceOrders(currentUserId, statuses);
            return ApiResponse.<java.util.List<com.cosmate.dto.response.ServiceOrderItemResponse>>builder().result(respList).build();
        } catch (Exception ex) {
            return ApiResponse.<java.util.List<com.cosmate.dto.response.ServiceOrderItemResponse>>builder().code(500).message("Failed to list provider service orders: " + ex.getMessage()).build();
        }
    }

    // List service orders (RENT_SERVICE) by cosplayer id in compact ServiceOrderItemResponse form (same as provider listing).
    // Accessible by the cosplayer themself or admin/staff.
    @GetMapping("/cosplayer/{userId}")
    public ApiResponse<java.util.List<com.cosmate.dto.response.ServiceOrderItemResponse>> listServiceOrdersByCosplayerId(@PathVariable Integer userId,
                                                                                                                 @RequestParam(required = false) String statuses) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Integer currentUserId = getCurrentUserId();
            boolean allowed = false;
            if (currentUserId != null && currentUserId.equals(userId)) allowed = true;
            else if (auth != null && auth.isAuthenticated()) {
                allowed = auth.getAuthorities().stream().anyMatch(a -> {
                    String at = a.getAuthority();
                    return "ROLE_ADMIN".equals(at) || "ROLE_STAFF".equals(at) || "ROLE_SUPERADMIN".equals(at)
                            || "ADMIN".equals(at) || "STAFF".equals(at) || "SUPERADMIN".equals(at);
                });
            }
            if (!allowed) return ApiResponse.<java.util.List<com.cosmate.dto.response.ServiceOrderItemResponse>>builder().code(403).message("Không có quyền truy cập danh sách đơn").build();

            java.util.List<com.cosmate.dto.response.ServiceOrderItemResponse> resp = orderService.listCosplayerServiceOrders(userId, statuses);
            return ApiResponse.<java.util.List<com.cosmate.dto.response.ServiceOrderItemResponse>>builder().result(resp == null ? java.util.Collections.emptyList() : resp).build();
        } catch (Exception ex) {
            return ApiResponse.<java.util.List<com.cosmate.dto.response.ServiceOrderItemResponse>>builder().code(500).message("Failed to list cosplayer service orders: " + ex.getMessage()).build();
        }
    }
}
