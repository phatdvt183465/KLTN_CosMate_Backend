package com.cosmate.controller;

import com.cosmate.dto.request.CreateOrderRequest;
import com.cosmate.dto.request.ShipOrderRequest;
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

import com.cosmate.repository.OrderImageRepository;
import com.cosmate.repository.OrderTrackingRepository;
import com.cosmate.service.FirebaseStorageService;
import org.springframework.web.multipart.MultipartFile;

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
    private final OrderImageRepository orderImageRepository;
    private final OrderTrackingRepository orderTrackingRepository;
    private final FirebaseStorageService firebaseStorageService;
    private final com.cosmate.repository.CostumeRepository costumeRepository;

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

    // helper to map Order entity to OrderFullResponse (reused by multiple endpoints)
    private OrderFullResponse toFullResponse(Order o) {
        OrderFullResponse resp = new OrderFullResponse();
        resp.setId(o.getId());
        resp.setCosplayerId(o.getCosplayerId());
        resp.setProviderId(o.getProviderId());
        resp.setOrderType(o.getOrderType());
        resp.setStatus(o.getStatus());
        resp.setTotalAmount(o.getTotalAmount());
        resp.setCreatedAt(o.getCreatedAt());

        List<OrderDetail> details = orderDetailRepository.findByOrderId(o.getId());
        resp.setDetails(details);
        resp.setSurcharges(orderCostumeSurchargeRepository.findByOrderId(o.getId()));
        resp.setAddresses(orderAddressRepository.findByOrderId(o.getId()));
        List<Integer> detailIds = details.stream().map(d -> d.getId()).toList();
        resp.setAccessories(detailIds.isEmpty() ? java.util.Collections.emptyList() : orderDetailAccessoryRepository.findByOrderDetailIdIn(detailIds));
        resp.setRentalOptions(detailIds.isEmpty() ? java.util.Collections.emptyList() : orderRentalOptionRepository.findByOrderDetailIdIn(detailIds));
        // images and trackings if available (images are now linked to order details)
        resp.setImages(detailIds.isEmpty() ? java.util.Collections.emptyList() : orderImageRepository.findByOrderDetailIdIn(detailIds));
        resp.setTrackings(orderTrackingRepository.findByOrderId(o.getId()));
        return resp;
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

        OrderFullResponse resp = toFullResponse(o);
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
                com.cosmate.entity.Transaction tx = walletService.credit(wallet, amount, "Order refund for cancellation", "ORDER_REFUND:" + order.getId(), null, order);
                refunded = (tx != null);
            }

            // update order status to CANCELLED
            order.setStatus("CANCELLED");
            orderRepository.save(order);

            // set related costumes back to AVAILABLE when order is cancelled
            try {
                List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
                if (details != null && !details.isEmpty()) {
                    for (OrderDetail d : details) {
                        if (d.getCostumeId() == null) continue;
                        Costume c = costumeRepository.findById(d.getCostumeId()).orElse(null);
                        if (c != null) {
                            c.setStatus("AVAILABLE");
                            costumeRepository.save(c);
                        }
                    }
                }
            } catch (Exception ex) {
                // ignore failures to update costume statuses - do not block cancellation
            }

            String actor = isCosplayer ? "cosplayer" : (isProviderOwner ? "provider" : "staff");
            String msg = "Order cancelled by " + actor + (refunded ? " and refunded" : "");
            return ApiResponse.<String>builder().result("OK").message(msg).build();
        } catch (Exception ex) {
            return ApiResponse.<String>builder().code(500).message("Failed to cancel order: " + ex.getMessage()).build();
        }
    }

    @PostMapping(path = "/{id}/ship", consumes = {"multipart/form-data"})
    public ApiResponse<?> shipOrder(@PathVariable Integer id,
                                   @RequestParam("trackingCode") String trackingCode,
                                   @RequestPart(value = "images", required = false) MultipartFile[] images,
                                   @RequestParam(value = "notes", required = false) List<String> notes) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<Object>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();

            // only provider owner or admin/staff allowed
            boolean isAdminStaff = hasAdminStaffRole(auth);
            boolean isProviderOwner = false;
            try {
                Provider p = providerService.getByUserId(currentUserId);
                if (p != null && p.getId() != null) {
                    isProviderOwner = true;
                }
            } catch (Exception ex) { }
            if (!isAdminStaff && !isProviderOwner) return ApiResponse.<Object>builder().code(403).message("Không có quyền thực hiện").build();

            Order order = orderRepository.findById(id).orElse(null);
            if (order == null) return ApiResponse.<Object>builder().code(404).message("Order not found").build();

            // provider owner must match order.providerId
            if (!isAdminStaff) {
                Provider p = providerService.getByUserId(currentUserId);
                if (p == null || p.getId() == null || !p.getId().equals(order.getProviderId())) {
                    return ApiResponse.<Object>builder().code(403).message("Không có quyền thực hiện cho đơn hàng này").build();
                }
            }

            // order must be PREPARING to move to SHIPPING_OUT
            if (!"PREPARING".equals(order.getStatus())) {
                return ApiResponse.<Object>builder().code(400).message("Order must be in PREPARING status to ship").build();
            }

            // validate request
            if (trackingCode == null || trackingCode.isBlank()) {
                return ApiResponse.<Object>builder().code(400).message("trackingCode is required").build();
            }
            if (images == null || images.length == 0) {
                return ApiResponse.<Object>builder().code(400).message("At least one image file is required").build();
            }

            // persist tracking
            OrderTracking ot = OrderTracking.builder()
                    .order(order)
                    .trackingCode(trackingCode)
                    .trackingStatus("CREATED")
                    .stage("SHIPPING_OUT")
                    .build();
            ot = orderTrackingRepository.save(ot);

            // upload images to firebase and persist OrderImage
            List<Integer> savedImageIds = new java.util.ArrayList<>();
            for (int i = 0; i < images.length; i++) {
                MultipartFile file = images[i];
                if (file == null || file.isEmpty()) continue;
                // build path: orders/{orderId}/shipping/{timestamp}_{originalFilename}
                String original = file.getOriginalFilename();
                String safeName = original == null ? String.valueOf(System.currentTimeMillis()) : original.replaceAll("[^a-zA-Z0-9._-]", "_");
                String path = String.format("orders/%d/shipping/%d_%s", order.getId(), System.currentTimeMillis(), safeName);
                String url = firebaseStorageService.uploadFile(file, path);

                String note = (notes != null && notes.size() > i) ? notes.get(i) : null;
                // associate image with the first order detail for this order (fallback)
                java.util.List<OrderDetail> detailsForOrder = orderDetailRepository.findByOrderId(order.getId());
                OrderDetail detailForImage = detailsForOrder.isEmpty() ? null : detailsForOrder.get(0);
                OrderImage oi = OrderImage.builder()
                        .orderDetail(detailForImage)
                        .imageUrl(url)
                        .stage("SHIPPING_OUT")
                        .note(note)
                        .confirm(false)
                        .build();
                oi = orderImageRepository.save(oi);
                savedImageIds.add(oi.getId());
            }

            // update order status
            order.setStatus("SHIPPING_OUT");
            orderRepository.save(order);

            java.util.Map<String, Object> result = new java.util.HashMap<>();
            // return the full tracking object and the uploaded image records for client display
            result.put("tracking", ot);
            List<Integer> detailIdsForImages = orderDetailRepository.findByOrderId(order.getId()).stream().map(d -> d.getId()).toList();
            List<OrderImage> uploadedImages = detailIdsForImages.isEmpty() ? java.util.Collections.emptyList() : orderImageRepository.findByOrderDetailIdIn(detailIdsForImages);
            result.put("images", uploadedImages);
            return ApiResponse.<Object>builder().result(result).message("Order updated to SHIPPING_OUT").build();
        } catch (Exception ex) {
            return ApiResponse.<Object>builder().code(500).message("Failed to ship order: " + ex.getMessage()).build();
        }
    }

    // Provider (owner) or staff/admin marks order as DELIVERING_OUT when shipment is out for delivery
    @PostMapping("/{id}/deliver-out")
    public ApiResponse<?> markDeliveringOut(@PathVariable Integer id) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<Object>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();

            Order order = orderRepository.findById(id).orElse(null);
            if (order == null) return ApiResponse.<Object>builder().code(404).message("Order not found").build();

            boolean isAdminStaff = hasAdminStaffRole(auth);
            boolean isProviderOwner = false;
            try {
                Provider p = providerService.getByUserId(currentUserId);
                if (p != null && p.getId() != null && p.getId().equals(order.getProviderId())) isProviderOwner = true;
            } catch (Exception ex) { }

            if (!isAdminStaff && !isProviderOwner) return ApiResponse.<Object>builder().code(403).message("Không có quyền thực hiện").build();

            // must be currently SHIPPING_OUT
            if (!"SHIPPING_OUT".equals(order.getStatus())) {
                return ApiResponse.<Object>builder().code(400).message("Order must be in SHIPPING_OUT status to mark delivering out").build();
            }

            // create tracking entry marking delivering
            // try to reuse last tracking code if available
            List<OrderTracking> existing = orderTrackingRepository.findByOrderId(order.getId());
            String trackingCode = null;
            if (existing != null && !existing.isEmpty()) {
                trackingCode = existing.get(existing.size()-1).getTrackingCode();
            }
            OrderTracking ot = OrderTracking.builder()
                    .order(order)
                    .trackingCode(trackingCode)
                    .trackingStatus("DELIVERING")
                    .stage("DELIVERING_OUT")
                    .build();
            ot = orderTrackingRepository.save(ot);

            order.setStatus("DELIVERING_OUT");
            orderRepository.save(order);

            java.util.Map<String,Object> res = new java.util.HashMap<>();
            res.put("tracking", ot);
            res.put("orderStatus", order.getStatus());
            return ApiResponse.<Object>builder().result(res).message("Order updated to DELIVERING_OUT").build();
        } catch (Exception ex) {
            return ApiResponse.<Object>builder().code(500).message("Failed to mark delivering out: " + ex.getMessage()).build();
        }
    }

    // Cosplayer confirms delivery and marks order as IN_USE. Optionally upload received images before confirming.
    @PostMapping(path = "/{id}/confirm-delivery", consumes = {"multipart/form-data"})
    public ApiResponse<?> confirmDelivery(@PathVariable Integer id,
                                          @RequestPart(value = "images", required = false) MultipartFile[] images,
                                          @RequestParam(value = "notes", required = false) List<String> notes) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<Object>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();

            Order order = orderRepository.findById(id).orElse(null);
            if (order == null) return ApiResponse.<Object>builder().code(404).message("Order not found").build();

            if (!currentUserId.equals(order.getCosplayerId())) return ApiResponse.<Object>builder().code(403).message("Order does not belong to user").build();

            if (!"DELIVERING_OUT".equals(order.getStatus())) {
                return ApiResponse.<Object>builder().code(400).message("Order must be in DELIVERING_OUT status to confirm delivery").build();
            }

            // If images provided, upload them to Firebase and persist OrderImage with stage IN_USE
            List<OrderImage> uploadedImages = new java.util.ArrayList<>();
            if (images != null && images.length > 0) {
                for (int i = 0; i < images.length; i++) {
                    MultipartFile file = images[i];
                    if (file == null || file.isEmpty()) continue;
                    String original = file.getOriginalFilename();
                    String safeName = original == null ? String.valueOf(System.currentTimeMillis()) : original.replaceAll("[^a-zA-Z0-9._-]", "_");
                    String path = String.format("orders/%d/received/%d_%s", order.getId(), System.currentTimeMillis(), safeName);
                    String url = firebaseStorageService.uploadFile(file, path);

                    String note = (notes != null && notes.size() > i) ? notes.get(i) : null;
                    java.util.List<OrderDetail> detailsForOrder = orderDetailRepository.findByOrderId(order.getId());
                    OrderDetail detailForImage = detailsForOrder.isEmpty() ? null : detailsForOrder.get(0);
                    OrderImage oi = OrderImage.builder()
                            .orderDetail(detailForImage)
                            .imageUrl(url)
                            .stage("IN_USE")
                            .note(note)
                            .confirm(false)
                            .build();
                    oi = orderImageRepository.save(oi);
                    uploadedImages.add(oi);
                }
            }

            // create tracking entry marking delivering
            List<OrderTracking> existing = orderTrackingRepository.findByOrderId(order.getId());
            String trackingCode = null;
            if (existing != null && !existing.isEmpty()) trackingCode = existing.get(existing.size()-1).getTrackingCode();

            OrderTracking ot = OrderTracking.builder()
                    .order(order)
                    .trackingCode(trackingCode)
                    .trackingStatus("DELIVERED")
                    .stage("IN_USE")
                    .build();
            ot = orderTrackingRepository.save(ot);

            order.setStatus("IN_USE");
            orderRepository.save(order);

            // Mark any images that were uploaded during SHIPPING_OUT as confirmed
            List<Integer> detIdsForConfirm = orderDetailRepository.findByOrderId(order.getId()).stream().map(d -> d.getId()).toList();
            if (!detIdsForConfirm.isEmpty()) {
                List<OrderImage> imgsToCheck = orderImageRepository.findByOrderDetailIdIn(detIdsForConfirm);
                boolean changed = false;
                for (OrderImage img : imgsToCheck) {
                    if (img != null && img.getStage() != null && "SHIPPING_OUT".equalsIgnoreCase(img.getStage()) && (img.getConfirm() == null || !img.getConfirm())) {
                        img.setConfirm(true);
                        changed = true;
                    }
                }
                if (changed) orderImageRepository.saveAll(imgsToCheck);
            }

            java.util.Map<String,Object> res = new java.util.HashMap<>();
            res.put("tracking", ot);
            res.put("orderStatus", order.getStatus());
            res.put("uploadedImages", uploadedImages);
            return ApiResponse.<Object>builder().result(res).message("Order updated to IN_USE").build();
        } catch (Exception ex) {
            return ApiResponse.<Object>builder().code(500).message("Failed to confirm delivery: " + ex.getMessage()).build();
        }
    }

    // Provider (owner) or staff/admin can mark an order as PREPARING (typically when they start preparing the package).
    @PostMapping("/{id}/prepare")
    public ApiResponse<String> prepareOrder(@PathVariable Integer id) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<String>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();

            Order order = orderRepository.findById(id).orElse(null);
            if (order == null) return ApiResponse.<String>builder().code(404).message("Order not found").build();

            boolean isAdminStaff = hasAdminStaffRole(auth);
            boolean isProviderOwner = false;
            try {
                Provider p = providerService.getByUserId(currentUserId);
                if (p != null && p.getId() != null && p.getId().equals(order.getProviderId())) isProviderOwner = true;
            } catch (Exception ex) { /* ignore */ }

            if (!isAdminStaff && !isProviderOwner) {
                return ApiResponse.<String>builder().code(403).message("Không có quyền thực hiện").build();
            }

            // only allow transition from PAID -> PREPARING
            String status = order.getStatus();
            if (!"PAID".equals(status)) {
                return ApiResponse.<String>builder().code(400).message("Order must be in PAID status to move to PREPARING").build();
            }

            order.setStatus("PREPARING");
            orderRepository.save(order);

            return ApiResponse.<String>builder().result("OK").message("Order status updated to PREPARING").build();
        } catch (Exception ex) {
            return ApiResponse.<String>builder().code(500).message("Failed to update order to PREPARING: " + ex.getMessage()).build();
        }
    }

    // Cosplayer starts return process: upload one or more images and provide tracking code -> move to SHIPPING_BACK
    @PostMapping(path = "/{id}/return", consumes = {"multipart/form-data"})
    public ApiResponse<?> startReturn(@PathVariable Integer id,
                                      @RequestParam("trackingCode") String trackingCode,
                                      @RequestPart(value = "images", required = false) MultipartFile[] images,
                                      @RequestParam(value = "notes", required = false) List<String> notes) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<Object>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();

            Order order = orderRepository.findById(id).orElse(null);
            if (order == null) return ApiResponse.<Object>builder().code(404).message("Order not found").build();

            // only cosplayer (order owner) can initiate return
            if (!currentUserId.equals(order.getCosplayerId())) return ApiResponse.<Object>builder().code(403).message("Order does not belong to user").build();

            // only allow when currently IN_USE (renter has the items)
            if (!"IN_USE".equals(order.getStatus())) {
                return ApiResponse.<Object>builder().code(400).message("Order must be in IN_USE status to start return").build();
            }

            // validate
            if (trackingCode == null || trackingCode.isBlank()) {
                return ApiResponse.<Object>builder().code(400).message("trackingCode is required").build();
            }
            if (images == null || images.length == 0) {
                return ApiResponse.<Object>builder().code(400).message("At least one image file is required to start return").build();
            }

            // persist return tracking
            OrderTracking ot = OrderTracking.builder()
                    .order(order)
                    .trackingCode(trackingCode)
                    .trackingStatus("RETURN_CREATED")
                    .stage("SHIPPING_BACK")
                    .build();
            ot = orderTrackingRepository.save(ot);

            // upload images to firebase and persist OrderImage with stage SHIPPING_BACK
            List<OrderImage> uploadedImages = new java.util.ArrayList<>();
            for (int i = 0; i < images.length; i++) {
                MultipartFile file = images[i];
                if (file == null || file.isEmpty()) continue;
                String original = file.getOriginalFilename();
                String safeName = original == null ? String.valueOf(System.currentTimeMillis()) : original.replaceAll("[^a-zA-Z0-9._-]", "_");
                String path = String.format("orders/%d/return/%d_%s", order.getId(), System.currentTimeMillis(), safeName);
                String url = firebaseStorageService.uploadFile(file, path);

                String note = (notes != null && notes.size() > i) ? notes.get(i) : null;
                java.util.List<OrderDetail> detailsForOrder = orderDetailRepository.findByOrderId(order.getId());
                OrderDetail detailForImage = detailsForOrder.isEmpty() ? null : detailsForOrder.get(0);
                OrderImage oi = OrderImage.builder()
                        .orderDetail(detailForImage)
                        .imageUrl(url)
                        .stage("SHIPPING_BACK")
                        .note(note)
                        .confirm(false)
                        .build();
                oi = orderImageRepository.save(oi);
                uploadedImages.add(oi);
            }

            // update order status
            order.setStatus("SHIPPING_BACK");
            orderRepository.save(order);

            java.util.Map<String,Object> res = new java.util.HashMap<>();
            res.put("tracking", ot);
            res.put("orderStatus", order.getStatus());
            List<Integer> detIds = orderDetailRepository.findByOrderId(order.getId()).stream().map(d -> d.getId()).toList();
            res.put("uploadedImages", detIds.isEmpty() ? java.util.Collections.emptyList() : orderImageRepository.findByOrderDetailIdIn(detIds));
            return ApiResponse.<Object>builder().result(res).message("Order updated to SHIPPING_BACK").build();
        } catch (Exception ex) {
            return ApiResponse.<Object>builder().code(500).message("Failed to start return: " + ex.getMessage()).build();
        }
    }

    // Provider confirms returned items OK and completes the order
    @PostMapping("/{id}/complete")
    public ApiResponse<?> completeOrder(@PathVariable Integer id) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<Object>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();

            Order order = orderRepository.findById(id).orElse(null);
            if (order == null) return ApiResponse.<Object>builder().code(404).message("Order not found").build();

            boolean isAdminStaff = hasAdminStaffRole(auth);
            boolean isProviderOwner = false;
            try {
                Provider p = providerService.getByUserId(currentUserId);
                if (p != null && p.getId() != null && p.getId().equals(order.getProviderId())) isProviderOwner = true;
            } catch (Exception ex) { /* ignore */ }

            if (!isAdminStaff && !isProviderOwner) return ApiResponse.<Object>builder().code(403).message("Không có quyền thực hiện").build();

            // Only allow completion when order is in SHIPPING_BACK (returned and on the way back or arrived)
            if (!"SHIPPING_BACK".equals(order.getStatus())) {
                return ApiResponse.<Object>builder().code(400).message("Order must be in SHIPPING_BACK status to complete").build();
            }

            // create tracking entry marking complete/received
            List<OrderTracking> existing = orderTrackingRepository.findByOrderId(order.getId());
            String trackingCode = null;
            if (existing != null && !existing.isEmpty()) trackingCode = existing.get(existing.size()-1).getTrackingCode();

            OrderTracking ot = OrderTracking.builder()
                    .order(order)
                    .trackingCode(trackingCode)
                    .trackingStatus("RETURN_RECEIVED")
                    .stage("COMPLETED")
                    .build();
            ot = orderTrackingRepository.save(ot);

            // --- Funds distribution: sum deposit from order details -> cosplayer; remaining -> provider ---
            java.math.BigDecimal total = order.getTotalAmount() == null ? java.math.BigDecimal.ZERO : order.getTotalAmount();

            // Mark images uploaded during SHIPPING_BACK as confirmed
            List<Integer> detailIdsForConfirmBack = orderDetailRepository.findByOrderId(order.getId()).stream().map(d -> d.getId()).toList();
            if (!detailIdsForConfirmBack.isEmpty()) {
                List<OrderImage> backImgs = orderImageRepository.findByOrderDetailIdIn(detailIdsForConfirmBack);
                boolean anyChanged = false;
                for (OrderImage img : backImgs) {
                    if (img != null && img.getStage() != null && "SHIPPING_BACK".equalsIgnoreCase(img.getStage()) && (img.getConfirm() == null || !img.getConfirm())) {
                        img.setConfirm(true);
                        anyChanged = true;
                    }
                }
                if (anyChanged) orderImageRepository.saveAll(backImgs);
            }

            // Sum deposit amounts from order details (deposit may be stored on details)
            List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
            java.math.BigDecimal depositTotal = java.math.BigDecimal.ZERO;
            if (details != null && !details.isEmpty()) {
                for (OrderDetail d : details) {
                    try {
                        java.math.BigDecimal dep = d.getDepositAmount() == null ? java.math.BigDecimal.ZERO : d.getDepositAmount();
                        depositTotal = depositTotal.add(dep);
                    } catch (Exception ex) {
                        // if detail has no deposit field, ignore
                    }
                }
            }

            if (depositTotal == null) depositTotal = java.math.BigDecimal.ZERO;
            java.math.BigDecimal providerShare = total.subtract(depositTotal);
            if (providerShare == null) providerShare = java.math.BigDecimal.ZERO;
            if (providerShare.compareTo(java.math.BigDecimal.ZERO) < 0) providerShare = java.math.BigDecimal.ZERO;

            java.util.List<com.cosmate.entity.Transaction> txs = new java.util.ArrayList<>();

            // return deposit to cosplayer if any
            if (depositTotal.compareTo(java.math.BigDecimal.ZERO) > 0) {
                com.cosmate.entity.User cosUser = com.cosmate.entity.User.builder().id(order.getCosplayerId()).build();
                com.cosmate.entity.Wallet cosWallet = walletService.createForUser(cosUser);
                com.cosmate.entity.Transaction txDeposit = walletService.credit(cosWallet, depositTotal, "Deposit returned on order completion", "DEPOSIT_RETURN:" + order.getId(), null, order);
                if (txDeposit != null) txs.add(txDeposit);
            }

            // credit provider with remaining amount (if > 0)
            if (providerShare.compareTo(java.math.BigDecimal.ZERO) > 0) {
                // NOTE: assumes order.getProviderId() can be used as a user id for wallet. If provider has separate userId, adjust accordingly.
                com.cosmate.entity.User provUser = com.cosmate.entity.User.builder().id(order.getProviderId()).build();
                com.cosmate.entity.Wallet provWallet = walletService.createForUser(provUser);
                com.cosmate.entity.Transaction txProv = walletService.credit(provWallet, providerShare, "Provider payout on order completion", "PROVIDER_PAYOUT:" + order.getId(), null, order);
                if (txProv != null) txs.add(txProv);
            }

            // finalize order status
            order.setStatus("COMPLETED");
            orderRepository.save(order);

            // set related costumes back to AVAILABLE when order completes
            try {
                if (details != null && !details.isEmpty()) {
                    for (OrderDetail d : details) {
                        if (d.getCostumeId() == null) continue;
                        Costume c = costumeRepository.findById(d.getCostumeId()).orElse(null);
                        if (c != null) {
                            c.setStatus("AVAILABLE");
                            costumeRepository.save(c);
                        }
                    }
                }
            } catch (Exception ex) {
                // ignore failures to update costume statuses - do not block completion
            }

            java.util.Map<String,Object> res = new java.util.HashMap<>();
            res.put("tracking", ot);
            res.put("orderStatus", order.getStatus());
            res.put("transactions", txs);
            res.put("depositReturned", depositTotal);
            res.put("providerPayout", providerShare);

            return ApiResponse.<Object>builder().result(res).message("Order marked as COMPLETED and funds distributed").build();
        } catch (Exception ex) {
            return ApiResponse.<Object>builder().code(500).message("Failed to complete order: " + ex.getMessage()).build();
        }
    }

    // New endpoint: dropdown list for orders filtered by orderType and statuses
    @GetMapping("/dropdown")
    public ApiResponse<?> dropdown(
            @RequestParam(required = false) String orderType,
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
                // For full=true, get compact list then fetch full details per id (preserves filtering logic in service)
                List<com.cosmate.dto.response.OrderDropdownResponse> compact = orderService.listOrdersForDropdown(orderType, statuses, providerId, cosplayerId);
                List<OrderFullResponse> fullList = compact.stream().map(d -> {
                    Order ord = orderRepository.findById(d.getId()).orElse(null);
                    return ord == null ? null : toFullResponse(ord);
                }).filter(x -> x != null).toList();
                return ApiResponse.<List<OrderFullResponse>>builder().result(fullList).build();
            }
        } catch (Exception ex) {
            return ApiResponse.<List<com.cosmate.dto.response.OrderDropdownResponse>>builder().code(500).message("Failed to get dropdown list: " + ex.getMessage()).build();
        }
    }

    // New endpoint: provide dropdown options for orderType and statuses
    @GetMapping("/dropdown-options")
    public ApiResponse<java.util.Map<String, Object>> dropdownOptions() {
        java.util.Map<String, Object> res = new java.util.HashMap<>();
        // order types
        java.util.List<String> orderTypes = java.util.Arrays.asList("RENT_COSTUME", "RENT_SERVICE");
        res.put("orderTypes", orderTypes);

        // statuses per type
        java.util.List<String> rentCostumeStatuses = java.util.Arrays.asList(
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
        java.util.List<String> rentServiceStatuses = java.util.Arrays.asList(
                "UNPAID",
                "PAID",
                "WAITING_SERVICE_DATE",
                "IN_SERVICE",
                "COMPLETED",
                "DISPUTE",
                "CANCELLED"
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
            Order order = orderRepository.findById(id).orElse(null);
            if (order == null) return ApiResponse.<java.util.List<com.cosmate.dto.response.TransactionResponse>>builder().code(404).message("Order not found").build();
            List<com.cosmate.entity.Transaction> txs = transactionRepository.findByOrder_IdOrderByCreatedAtDesc(id);
            java.util.List<com.cosmate.dto.response.TransactionResponse> resp = txs.stream().map(t -> {
                return com.cosmate.dto.response.TransactionResponse.builder()
                        .id(t.getId())
                        .amount(t.getAmount())
                        .type(t.getType())
                        .status(t.getStatus())
                        .paymentMethod(t.getPaymentMethod())
                        .createdAt(t.getCreatedAt())
                        .build();
            }).toList();
            return ApiResponse.<java.util.List<com.cosmate.dto.response.TransactionResponse>>builder().result(resp).build();
        } catch (Exception ex) {
            return ApiResponse.<java.util.List<com.cosmate.dto.response.TransactionResponse>>builder().code(500).message("Failed to fetch transactions: " + ex.getMessage()).build();
        }
    }
}
