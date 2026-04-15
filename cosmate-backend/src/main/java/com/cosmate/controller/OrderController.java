package com.cosmate.controller;

import com.cosmate.dto.request.CreateOrderRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.OrderFullResponse;
import com.cosmate.dto.response.OrderResponse;
import com.cosmate.service.OrderService;
import com.cosmate.service.ProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final ProviderService providerService;
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

    private boolean hasAdminStaffRole(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> {
            String at = a.getAuthority();
            return "ROLE_ADMIN".equals(at) || "ROLE_STAFF".equals(at) || "ROLE_SUPERADMIN".equals(at)
                    || "ADMIN".equals(at) || "STAFF".equals(at) || "SUPERADMIN".equals(at);
        });
    }

    @PostMapping
    public ApiResponse<OrderResponse> createOrder(@RequestParam Integer cosplayerId,
                                                  @Valid @RequestBody CreateOrderRequest request) {
        try {
            OrderResponse resp = orderService.createOrder(cosplayerId, request);
            return ApiResponse.<OrderResponse>builder().result(resp).message("Order created").build();
        } catch (IllegalArgumentException ex) {
            return ApiResponse.<OrderResponse>builder().code(400).message(ex.getMessage()).build();
        } catch (Exception ex) {
            return ApiResponse.<OrderResponse>builder().code(500).message("Failed to create order: " + ex.getMessage()).build();
        }
    }

    @PostMapping("/{id}/pay")
    public ApiResponse<OrderResponse> payOrder(@RequestParam Integer cosplayerId,
                                               @PathVariable Integer id,
                                               @RequestParam(required = false) String paymentMethod,
                                               @RequestParam(required = false) String returnUrl) {
        try {
            OrderResponse resp = orderService.payOrder(cosplayerId, id, paymentMethod, returnUrl);
            return ApiResponse.<OrderResponse>builder().result(resp).message("Payment initiated").build();
        } catch (IllegalArgumentException ex) {
            return ApiResponse.<OrderResponse>builder().code(400).message(ex.getMessage()).build();
        } catch (Exception ex) {
            return ApiResponse.<OrderResponse>builder().code(500).message("Failed to process payment: " + ex.getMessage()).build();
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderFullResponse> getById(@PathVariable Integer id) {
        try {
            OrderFullResponse resp = orderService.getFullOrderById(id);
            if (resp == null) return ApiResponse.<OrderFullResponse>builder().code(404).message("Không tìm thấy đơn hàng").build();
            return ApiResponse.<OrderFullResponse>builder().result(resp).build();
        } catch (Exception ex) {
            return ApiResponse.<OrderFullResponse>builder().code(500).message("Failed to fetch order: " + ex.getMessage()).build();
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderFullResponse>>> listAll() {
        ApiResponse<List<OrderFullResponse>> api = new ApiResponse<>();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            api.setCode(1001); api.setMessage("Chưa xác thực - Vui lòng đăng nhập");
            return ResponseEntity.status(401).body(api);
        }
        boolean allowed = auth.getAuthorities().stream().anyMatch(a ->
                "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_STAFF".equals(a.getAuthority()) || "ROLE_SUPERADMIN".equals(a.getAuthority())
        );
        if (!allowed) {
            api.setCode(1006); api.setMessage("Không có quyền truy cập danh sách đơn");
            return ResponseEntity.status(403).body(api);
        }
        try {
            java.util.List<OrderFullResponse> resp = orderService.listAllOrders();
            api.setCode(0); api.setMessage("OK"); api.setResult(resp);
            return ResponseEntity.ok(api);
        } catch (Exception ex) {
            api.setCode(500); api.setMessage("Failed to list orders: " + ex.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    @GetMapping("/provider/{providerId}")
    public ApiResponse<List<OrderFullResponse>> listByProvider(@PathVariable Integer providerId) {
        try {
            java.util.List<OrderFullResponse> resp = orderService.listOrdersByProvider(providerId);
            return ApiResponse.<List<OrderFullResponse>>builder().result(resp).build();
        } catch (Exception ex) {
            return ApiResponse.<List<OrderFullResponse>>builder().code(500).message("Failed to list orders: " + ex.getMessage()).build();
        }
    }

    @GetMapping("/provider/{providerId}/filter")
    public ApiResponse<List<OrderFullResponse>> filterByProviderAndStatuses(@PathVariable Integer providerId,
                                                                            @RequestParam(required = false, name = "status") List<String> statuses) {
        // authorization: only provider owner or admin/staff can access
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Integer currentUserId = getCurrentUserId();
        boolean allowed = false;
        if (auth != null && auth.isAuthenticated()) {
            if (hasAdminStaffRole(auth)) allowed = true;
            else if (currentUserId != null) {
                try {
                    com.cosmate.entity.Provider p = providerService.getByUserId(currentUserId);
                    if (p != null && p.getId() != null && p.getId().equals(providerId)) allowed = true;
                } catch (Exception ignored) {}
            }
        }
        if (!allowed) return ApiResponse.<List<OrderFullResponse>>builder().code(403).message("Không có quyền truy cập danh sách đơn").build();

        if (statuses == null || statuses.isEmpty()) {
            statuses = java.util.Arrays.asList(
                    "UNPAID","PAID","PREPARING","SHIPPING_OUT","DELIVERING_OUT","IN_USE","SHIPPING_BACK","COMPLETED","DISPUTE","CANCELLED","EXTENDING"
            );
        }
        try {
            boolean isAdminStaff = false;
            if (auth != null && auth.isAuthenticated()) isAdminStaff = hasAdminStaffRole(auth);
            java.util.List<OrderFullResponse> resp = orderService.filterOrdersByProviderAndStatuses(providerId, statuses, currentUserId, isAdminStaff);
            return ApiResponse.<List<OrderFullResponse>>builder().result(resp).build();
        } catch (Exception ex) {
            return ApiResponse.<List<OrderFullResponse>>builder().code(500).message("Failed to filter orders: " + ex.getMessage()).build();
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<OrderFullResponse>>> listByUserId(@PathVariable Integer userId) {
        ApiResponse<List<OrderFullResponse>> api = new ApiResponse<>();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Integer currentUserId = getCurrentUserId();
        boolean allowed = false;
        if (currentUserId != null && currentUserId.equals(userId)) allowed = true;
        else if (auth != null && auth.isAuthenticated()) allowed = hasAdminStaffRole(auth);
        if (!allowed) { api.setCode(1006); api.setMessage("Không có quyền truy cập danh sách đơn"); return ResponseEntity.status(403).body(api); }
        try {
            java.util.List<OrderFullResponse> resp = orderService.listOrdersByUserId(userId);
            api.setCode(0); api.setMessage("OK"); api.setResult(resp);
            return ResponseEntity.ok(api);
        } catch (Exception ex) {
            api.setCode(500); api.setMessage("Failed to list orders: " + ex.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<String> cancelOrder(@PathVariable Integer id) {
        try {
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<String>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();
            try {
                orderService.cancelOrder(currentUserId, id);
                return ApiResponse.<String>builder().result("OK").message("Order cancelled").build();
            } catch (IllegalArgumentException ex) {
                return ApiResponse.<String>builder().code(400).message(ex.getMessage()).build();
            }
        } catch (Exception ex) {
            return ApiResponse.<String>builder().code(500).message("Failed to cancel order: " + ex.getMessage()).build();
        }
    }

    @PostMapping(path = "/{id}/ship", consumes = {"multipart/form-data"})
    public ApiResponse<?> shipOrder(@PathVariable Integer id,
                                    @RequestParam("trackingCode") String trackingCode,
                                    @RequestParam(value = "shippingCarrierName", required = false) String shippingCarrierName,
                                    @RequestPart(value = "images", required = false) MultipartFile[] images,
                                    @RequestParam(value = "notes", required = false) List<String> notes) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<Object>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();
            boolean isAdminStaff = hasAdminStaffRole(auth);
            try {
                java.util.Map<String,Object> result = orderService.shipOrder(currentUserId, id, trackingCode, shippingCarrierName, images, notes, isAdminStaff);
                return ApiResponse.<Object>builder().result(result).message("Order updated to SHIPPING_OUT").build();
            } catch (IllegalArgumentException ex) {
                return ApiResponse.<Object>builder().code(400).message(ex.getMessage()).build();
            }
        } catch (Exception ex) {
            return ApiResponse.<Object>builder().code(500).message("Failed to ship order: " + ex.getMessage()).build();
        }
    }

    @PostMapping("/{id}/deliver-out")
    public ApiResponse<?> markDeliveringOut(@PathVariable Integer id) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<Object>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();
            boolean isAdminStaff = hasAdminStaffRole(auth);
            try {
                java.util.Map<String,Object> res = orderService.markDeliveringOut(currentUserId, id, isAdminStaff);
                return ApiResponse.<Object>builder().result(res).message("Order updated to DELIVERING_OUT").build();
            } catch (IllegalArgumentException ex) {
                return ApiResponse.<Object>builder().code(400).message(ex.getMessage()).build();
            }
        } catch (Exception ex) {
            return ApiResponse.<Object>builder().code(500).message("Failed to mark delivering out: " + ex.getMessage()).build();
        }
    }

    @PostMapping(path = "/{id}/confirm-delivery", consumes = {"multipart/form-data"})
    public ApiResponse<?> confirmDelivery(@PathVariable Integer id,
                                          @RequestPart(value = "images", required = false) MultipartFile[] images,
                                          @RequestParam(value = "notes", required = false) List<String> notes) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<Object>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();
            try {
                java.util.Map<String,Object> res = orderService.confirmDelivery(currentUserId, id, images, notes);
                return ApiResponse.<Object>builder().result(res).message("Order updated to IN_USE").build();
            } catch (IllegalArgumentException ex) {
                return ApiResponse.<Object>builder().code(400).message(ex.getMessage()).build();
            }
        } catch (Exception ex) {
            return ApiResponse.<Object>builder().code(500).message("Failed to confirm delivery: " + ex.getMessage()).build();
        }
    }

    @PostMapping("/{id}/prepare")
    public ApiResponse<String> prepareOrder(@PathVariable Integer id) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<String>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();
            boolean isAdminStaff = hasAdminStaffRole(auth);
            try {
                String res = orderService.prepareOrder(currentUserId, id, isAdminStaff);
                return ApiResponse.<String>builder().result(res).message("Order status updated to PREPARING").build();
            } catch (IllegalArgumentException ex) {
                return ApiResponse.<String>builder().code(400).message(ex.getMessage()).build();
            }
        } catch (Exception ex) {
            return ApiResponse.<String>builder().code(500).message("Failed to update order to PREPARING: " + ex.getMessage()).build();
        }
    }

    @PostMapping(path = "/{id}/return", consumes = {"multipart/form-data"})
    public ApiResponse<?> startReturn(@PathVariable Integer id,
                                      @RequestParam("trackingCode") String trackingCode,
                                      @RequestParam(value = "shippingCarrierName", required = false) String shippingCarrierName,
                                      @RequestPart(value = "images", required = false) MultipartFile[] images,
                                      @RequestParam(value = "notes", required = false) List<String> notes) {
        try {
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<Object>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();
            try {
                java.util.Map<String,Object> res = orderService.startReturn(currentUserId, id, trackingCode, shippingCarrierName, images, notes);
                return ApiResponse.<Object>builder().result(res).message("Order updated to SHIPPING_BACK").build();
            } catch (IllegalArgumentException ex) {
                return ApiResponse.<Object>builder().code(400).message(ex.getMessage()).build();
            }
        } catch (Exception ex) {
            return ApiResponse.<Object>builder().code(500).message("Failed to start return: " + ex.getMessage()).build();
        }
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<?> completeOrder(@PathVariable Integer id) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<Object>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();
            boolean isAdminStaff = hasAdminStaffRole(auth);
            try {
                java.util.Map<String,Object> res = orderService.completeOrder(currentUserId, id, isAdminStaff);
                return ApiResponse.<Object>builder().result(res).message("Order marked as COMPLETED and funds distributed").build();
            } catch (IllegalArgumentException ex) {
                return ApiResponse.<Object>builder().code(400).message(ex.getMessage()).build();
            }
        } catch (Exception ex) {
            return ApiResponse.<Object>builder().code(500).message("Failed to complete order: " + ex.getMessage()).build();
        }
    }

    @GetMapping("/dropdown")
    public ApiResponse<?> dropdown(@RequestParam(required = false) String orderType,
                                   @RequestParam(required = false, name = "status") List<String> statuses,
                                   @RequestParam(required = false) Integer providerId,
                                   @RequestParam(required = false) Integer cosplayerId,
                                   @RequestParam(required = false, defaultValue = "false") boolean full) {
        try {
            if (statuses == null) {
                // leave null -> service will apply defaults
            } else if (statuses.size() == 1 && statuses.get(0) != null && statuses.get(0).contains(",")) {
                String combined = statuses.get(0);
                statuses = java.util.Arrays.asList(combined.split(","));
            }
            if (!full) {
                List<com.cosmate.dto.response.OrderDropdownResponse> list = orderService.listOrdersForDropdown(orderType, statuses, providerId, cosplayerId);
                return ApiResponse.<List<com.cosmate.dto.response.OrderDropdownResponse>>builder().result(list).build();
            } else {
                List<com.cosmate.dto.response.OrderDropdownResponse> compact = orderService.listOrdersForDropdown(orderType, statuses, providerId, cosplayerId);
                List<OrderFullResponse> fullList = compact.stream().map(d -> {
                    try { return orderService.getFullOrderById(d.getId()); } catch (Exception e) { return null; }
                }).filter(x -> x != null).toList();
                return ApiResponse.<List<OrderFullResponse>>builder().result(fullList).build();
            }
        } catch (Exception ex) {
            return ApiResponse.<List<com.cosmate.dto.response.OrderDropdownResponse>>builder().code(500).message("Failed to get dropdown list: " + ex.getMessage()).build();
        }
    }

    @GetMapping("/dropdown-options")
    public ApiResponse<java.util.Map<String, Object>> dropdownOptions() {
        java.util.Map<String, Object> res = new java.util.HashMap<>();
        java.util.List<String> orderTypes = java.util.Arrays.asList("RENT_COSTUME", "RENT_SERVICE");
        res.put("orderTypes", orderTypes);
        java.util.List<String> rentCostumeStatuses = java.util.Arrays.asList(
                "UNPAID","PAID","PREPARING","SHIPPING_OUT","DELIVERING_OUT","IN_USE","SHIPPING_BACK","COMPLETED","DISPUTE","CANCELLED","EXTENDING"
        );
        java.util.List<String> rentServiceStatuses = java.util.Arrays.asList(
                "UNPAID","PAID","WAITING_SERVICE_DATE","IN_SERVICE","COMPLETED","DISPUTE","CANCELLED"
        );
        java.util.Map<String, java.util.List<String>> statusesMap = new java.util.HashMap<>();
        statusesMap.put("RENT_COSTUME", rentCostumeStatuses);
        statusesMap.put("RENT_SERVICE", rentServiceStatuses);
        res.put("statusesByType", statusesMap);
        return ApiResponse.<java.util.Map<String, Object>>builder().result(res).build();
    }

    @GetMapping("/{id}/transactions")
    public ApiResponse<java.util.List<com.cosmate.dto.response.TransactionResponse>> getTransactionsForOrder(@PathVariable Integer id) {
        try {
            java.util.List<com.cosmate.dto.response.TransactionResponse> resp = orderService.getTransactionsForOrder(id);
            return ApiResponse.<java.util.List<com.cosmate.dto.response.TransactionResponse>>builder().result(resp).build();
        } catch (Exception ex) {
            return ApiResponse.<java.util.List<com.cosmate.dto.response.TransactionResponse>>builder().code(500).message("Failed to fetch transactions: " + ex.getMessage()).build();
        }
    }
}

