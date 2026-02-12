package com.cosmate.controller;

import com.cosmate.dto.request.CreateOrderRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.OrderFullResponse;
import com.cosmate.dto.response.OrderResponse;
import com.cosmate.entity.*;
import com.cosmate.repository.OrderCostumeSurchargeRepository;
import com.cosmate.repository.OrderDetailRepository;
import com.cosmate.repository.OrderRepository;
import com.cosmate.repository.OrderAddressRepository;
import com.cosmate.repository.OrderDetailAccessoryRepository;
import com.cosmate.repository.OrderRentalOptionRepository;
import com.cosmate.service.OrderService;
import com.cosmate.service.ProviderService;
import com.cosmate.service.WalletService;
import com.cosmate.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final OrderCostumeSurchargeRepository orderCostumeSurchargeRepository;
    private final OrderAddressRepository orderAddressRepository;
    private final OrderDetailAccessoryRepository orderDetailAccessoryRepository;
    private final OrderRentalOptionRepository orderRentalOptionRepository;
    private final ProviderService providerService;
    private final WalletService walletService;
    private final TransactionRepository transactionRepository;

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

    // helper to check admin/staff roles (accept both ROLE_ prefixed and raw names)
    private boolean hasAdminStaffRole(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> {
            String at = a.getAuthority();
            return "ROLE_ADMIN".equals(at) || "ROLE_STAFF".equals(at) || "ROLE_SUPERADMIN".equals(at)
                    || "ADMIN".equals(at) || "STAFF".equals(at) || "SUPERADMIN".equals(at);
        });
    }

    @PostMapping
    public ApiResponse<OrderResponse> createOrder(
            @RequestParam Integer cosplayerId,
            @RequestBody CreateOrderRequest request) {
        try {
            OrderResponse resp = orderService.createOrder(cosplayerId, request);
            return ApiResponse.<OrderResponse>builder()
                    .result(resp)
                    .message("Order created")
                    .build();
        } catch (IllegalArgumentException ex) {
            return ApiResponse.<OrderResponse>builder()
                    .code(400)
                    .message(ex.getMessage())
                    .build();
        } catch (Exception ex) {
            return ApiResponse.<OrderResponse>builder()
                    .code(500)
                    .message("Failed to create order: " + ex.getMessage())
                    .build();
        }
    }

    @PostMapping("/{id}/pay")
    public ApiResponse<OrderResponse> payOrder(
            @RequestParam Integer cosplayerId,
            @PathVariable Integer id,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String returnUrl) {
        try {
            OrderResponse resp = orderService.payOrder(cosplayerId, id, paymentMethod, returnUrl);
            return ApiResponse.<OrderResponse>builder()
                    .result(resp)
                    .message("Payment initiated")
                    .build();
        } catch (IllegalArgumentException ex) {
            return ApiResponse.<OrderResponse>builder()
                    .code(400)
                    .message(ex.getMessage())
                    .build();
        } catch (Exception ex) {
            return ApiResponse.<OrderResponse>builder()
                    .code(500)
                    .message("Failed to process payment: " + ex.getMessage())
                    .build();
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderFullResponse> getById(@PathVariable Integer id) {
        Order o = orderRepository.findById(id).orElse(null);
        if (o == null) return ApiResponse.<OrderFullResponse>builder().code(404).message("Không tìm thấy đơn hàng").build();

        List<OrderDetail> details = orderDetailRepository.findByOrderId(id);
        List<OrderCostumeSurcharge> sur = orderCostumeSurchargeRepository.findByOrderId(id);
        var addrs = orderAddressRepository.findByOrderId(id);

        // collect detail ids to fetch accessories/options
        List<Integer> detailIds = details.stream().map(d -> d.getId()).toList();
        List<OrderDetailAccessory> accessories = detailIds.isEmpty() ? java.util.Collections.emptyList() : orderDetailAccessoryRepository.findByOrderDetailIdIn(detailIds);
        List<OrderRentalOption> rentalOptions = detailIds.isEmpty() ? java.util.Collections.emptyList() : orderRentalOptionRepository.findByOrderDetailIdIn(detailIds);

        OrderFullResponse resp = new OrderFullResponse();
        resp.setId(o.getId());
        resp.setCosplayerId(o.getCosplayerId());
        resp.setProviderId(o.getProviderId());
        resp.setOrderType(o.getOrderType());
        resp.setStatus(o.getStatus());
        resp.setTotalAmount(o.getTotalAmount());
        resp.setCreatedAt(o.getCreatedAt());
        resp.setDetails(details);
        resp.setSurcharges(sur);
        resp.setAddresses(addrs);
        resp.setAccessories(accessories);
        resp.setRentalOptions(rentalOptions);

        return ApiResponse.<OrderFullResponse>builder().result(resp).build();
    }

    // List all orders (staff role required). Simple role check via header X-User-Role for demo.
    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderFullResponse>>> listAll() {
        ApiResponse<List<OrderFullResponse>> api = new ApiResponse<>();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            api.setCode(1001);
            api.setMessage("Chưa xác thực - Vui lòng đăng nhập");
            return ResponseEntity.status(401).body(api);
        }

        boolean allowed = auth.getAuthorities().stream().anyMatch(a ->
                "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_STAFF".equals(a.getAuthority()) || "ROLE_SUPERADMIN".equals(a.getAuthority())
        );
        if (!allowed) {
            api.setCode(1006);
            api.setMessage("Không có quyền truy cập danh sách đơn");
            return ResponseEntity.status(403).body(api);
        }

        List<Order> orders = orderRepository.findAllByOrderByCreatedAtDesc();
        List<OrderFullResponse> resp = orders.stream().map(o -> {
            OrderFullResponse r = new OrderFullResponse();
            r.setId(o.getId());
            r.setCosplayerId(o.getCosplayerId());
            r.setProviderId(o.getProviderId());
            r.setOrderType(o.getOrderType());
            r.setStatus(o.getStatus());
            r.setTotalAmount(o.getTotalAmount());
            r.setCreatedAt(o.getCreatedAt());
            List<OrderDetail> details = orderDetailRepository.findByOrderId(o.getId());
            r.setDetails(details);
            r.setSurcharges(orderCostumeSurchargeRepository.findByOrderId(o.getId()));
            r.setAddresses(orderAddressRepository.findByOrderId(o.getId()));
            List<Integer> detailIds = details.stream().map(d -> d.getId()).toList();
            r.setAccessories(detailIds.isEmpty() ? java.util.Collections.emptyList() : orderDetailAccessoryRepository.findByOrderDetailIdIn(detailIds));
            r.setRentalOptions(detailIds.isEmpty() ? java.util.Collections.emptyList() : orderRentalOptionRepository.findByOrderDetailIdIn(detailIds));
            return r;
        }).collect(Collectors.toList());
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(resp);
        return ResponseEntity.ok(api);
    }

    // List orders by provider id
    @GetMapping("/provider/{providerId}")
    public ApiResponse<List<OrderFullResponse>> listByProvider(@PathVariable Integer providerId) {
        List<Order> orders = orderRepository.findByProviderIdOrderByCreatedAtDesc(providerId);
        List<OrderFullResponse> resp = orders.stream().map(o -> {
            OrderFullResponse r = new OrderFullResponse();
            r.setId(o.getId());
            r.setCosplayerId(o.getCosplayerId());
            r.setProviderId(o.getProviderId());
            r.setOrderType(o.getOrderType());
            r.setStatus(o.getStatus());
            r.setTotalAmount(o.getTotalAmount());
            r.setCreatedAt(o.getCreatedAt());
            List<OrderDetail> details = orderDetailRepository.findByOrderId(o.getId());
            r.setDetails(details);
            r.setSurcharges(orderCostumeSurchargeRepository.findByOrderId(o.getId()));
            r.setAddresses(orderAddressRepository.findByOrderId(o.getId()));
            List<Integer> detailIds = details.stream().map(d -> d.getId()).toList();
            r.setAccessories(detailIds.isEmpty() ? java.util.Collections.emptyList() : orderDetailAccessoryRepository.findByOrderDetailIdIn(detailIds));
            r.setRentalOptions(detailIds.isEmpty() ? java.util.Collections.emptyList() : orderRentalOptionRepository.findByOrderDetailIdIn(detailIds));
            return r;
        }).collect(Collectors.toList());
        return ApiResponse.<List<OrderFullResponse>>builder().result(resp).build();
    }

    // New: provider filter endpoint to select orders by provider and multiple statuses
    @GetMapping("/provider/{providerId}/filter")
    public ApiResponse<List<OrderFullResponse>> filterByProviderAndStatuses(
            @PathVariable Integer providerId,
            @RequestParam(required = false, name = "status") List<String> statuses) {

        // authorization: only provider owner or admin/staff can access
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Integer currentUserId = getCurrentUserId();
        boolean allowed = false;
        if (auth != null && auth.isAuthenticated()) {
            if (hasAdminStaffRole(auth)) allowed = true;
            else if (currentUserId != null) {
                try {
                    Provider p = providerService.getByUserId(currentUserId);
                    if (p != null && p.getId() != null && p.getId().equals(providerId)) allowed = true;
                } catch (Exception ex) {
                    // ignore - not allowed
                }
            }
        }
        if (!allowed) return ApiResponse.<List<OrderFullResponse>>builder().code(403).message("Không có quyền truy cập danh sách đơn").build();

        // default statuses list if none provided
        if (statuses == null || statuses.isEmpty()) {
            statuses = java.util.Arrays.asList(
                    "UNPAID",
                    "PAID",
                    "PREPARING",
                    "SHIPPING_OUT",
                    "DELIVERING_OUT",
                    "IN_USE",
                    "SHIPPING_BACK",
                    "COMPLETED",
                    "DISPUTE",
                    "CANCELLED",
                    "EXTENDING"
            );
        }

        List<Order> orders = orderRepository.findByProviderIdAndStatusInOrderByCreatedAtDesc(providerId, statuses);
        List<OrderFullResponse> resp = orders.stream().map(o -> {
            OrderFullResponse r = new OrderFullResponse();
            r.setId(o.getId());
            r.setCosplayerId(o.getCosplayerId());
            r.setProviderId(o.getProviderId());
            r.setOrderType(o.getOrderType());
            r.setStatus(o.getStatus());
            r.setTotalAmount(o.getTotalAmount());
            r.setCreatedAt(o.getCreatedAt());
            List<OrderDetail> details = orderDetailRepository.findByOrderId(o.getId());
            r.setDetails(details);
            r.setSurcharges(orderCostumeSurchargeRepository.findByOrderId(o.getId()));
            r.setAddresses(orderAddressRepository.findByOrderId(o.getId()));
            List<Integer> detailIds = details.stream().map(d -> d.getId()).toList();
            r.setAccessories(detailIds.isEmpty() ? java.util.Collections.emptyList() : orderDetailAccessoryRepository.findByOrderDetailIdIn(detailIds));
            r.setRentalOptions(detailIds.isEmpty() ? java.util.Collections.emptyList() : orderRentalOptionRepository.findByOrderDetailIdIn(detailIds));
            return r;
        }).collect(Collectors.toList());

        return ApiResponse.<List<OrderFullResponse>>builder().result(resp).build();
    }

    // Get orders by a specific userId (owner or admin/staff/superadmin can access)
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<OrderFullResponse>>> listByUserId(@PathVariable Integer userId) {
        ApiResponse<List<OrderFullResponse>> api = new ApiResponse<>();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Integer currentUserId = getCurrentUserId();
        boolean allowed = false;
        if (currentUserId != null && currentUserId.equals(userId)) allowed = true;
        else if (auth != null && auth.isAuthenticated()) allowed = hasAdminStaffRole(auth);

        if (!allowed) { api.setCode(1006); api.setMessage("Không có quyền truy cập danh sách đơn"); return ResponseEntity.status(403).body(api); }

        List<Order> orders = orderRepository.findByCosplayerIdOrderByCreatedAtDesc(userId);
        List<OrderFullResponse> resp = orders.stream().map(o -> {
            OrderFullResponse r = new OrderFullResponse();
            r.setId(o.getId());
            r.setCosplayerId(o.getCosplayerId());
            r.setProviderId(o.getProviderId());
            r.setOrderType(o.getOrderType());
            r.setStatus(o.getStatus());
            r.setTotalAmount(o.getTotalAmount());
            r.setCreatedAt(o.getCreatedAt());
            List<OrderDetail> details = orderDetailRepository.findByOrderId(o.getId());
            r.setDetails(details);
            r.setSurcharges(orderCostumeSurchargeRepository.findByOrderId(o.getId()));
            r.setAddresses(orderAddressRepository.findByOrderId(o.getId()));
            List<Integer> detailIds = details.stream().map(d -> d.getId()).toList();
            r.setAccessories(detailIds.isEmpty() ? java.util.Collections.emptyList() : orderDetailAccessoryRepository.findByOrderDetailIdIn(detailIds));
            r.setRentalOptions(detailIds.isEmpty() ? java.util.Collections.emptyList() : orderRentalOptionRepository.findByOrderDetailIdIn(detailIds));
            return r;
        }).collect(Collectors.toList());

        api.setCode(0); api.setMessage("OK"); api.setResult(resp);
        return ResponseEntity.ok(api);
    }

    // Cosplayer / Provider / Staff can cancel an order. Cosplayer can cancel only when status is UNPAID or PAID.
    // Provider owner or admin/staff can cancel most statuses (except COMPLETED or already CANCELLED).
    @PostMapping("/{id}/cancel")
    public ApiResponse<String> cancelOrder(@PathVariable Integer id) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Integer currentUserId = getCurrentUserId();

            if (currentUserId == null) return ApiResponse.<String>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();

            Order order = orderRepository.findById(id).orElse(null);
            if (order == null) return ApiResponse.<String>builder().code(404).message("Order not found").build();

            String status = order.getStatus();

            boolean isAdminStaff = hasAdminStaffRole(auth);
            boolean isProviderOwner = false;
            try {
                Provider p = providerService.getByUserId(currentUserId);
                if (p != null && p.getId() != null && p.getId().equals(order.getProviderId())) isProviderOwner = true;
            } catch (Exception ex) {
                // ignore
            }
            boolean isCosplayer = currentUserId.equals(order.getCosplayerId());

            if (!isAdminStaff && !isProviderOwner && !isCosplayer) {
                return ApiResponse.<String>builder().code(403).message("Không có quyền hủy đơn").build();
            }

            // Decide allowed statuses depending on caller
            if (isCosplayer) {
                // Cosplayer may only cancel when UNPAID or PAID
                if (status == null || !("UNPAID".equals(status) || "PAID".equals(status))) {
                    return ApiResponse.<String>builder().code(400).message("Không thể hủy đơn trong trạng thái: " + status).build();
                }
            } else {
                // Provider owner or admin/staff: disallow cancelling COMPLETED or already CANCELLED
                if ("COMPLETED".equals(status) || "CANCELLED".equals(status)) {
                    return ApiResponse.<String>builder().code(400).message("Không thể hủy đơn trong trạng thái: " + status).build();
                }
            }

            boolean refunded = false;
            // If order was PAID, refund totalAmount to cosplayer's wallet
            if ("PAID".equals(status)) {
                java.math.BigDecimal amount = order.getTotalAmount() == null ? java.math.BigDecimal.ZERO : order.getTotalAmount();
                com.cosmate.entity.User u = com.cosmate.entity.User.builder().id(order.getCosplayerId()).build();
                com.cosmate.entity.Wallet wallet = walletService.createForUser(u);
                com.cosmate.entity.Transaction tx = walletService.credit(wallet, amount, "Order refund for cancellation", "ORDER_REFUND:" + order.getId());
                refunded = (tx != null);
            }

            // update order status to CANCELLED
            order.setStatus("CANCELLED");
            orderRepository.save(order);

            String actor = isCosplayer ? "cosplayer" : (isProviderOwner ? "provider" : "staff");
            String msg = "Order cancelled by " + actor + (refunded ? " and refunded" : "");
            return ApiResponse.<String>builder().result("OK").message(msg).build();
        } catch (Exception ex) {
            return ApiResponse.<String>builder().code(500).message("Failed to cancel order: " + ex.getMessage()).build();
        }
    }
}
