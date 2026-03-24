package com.cosmate.controller;

import com.cosmate.dto.request.CreateServiceOrderRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.OrderResponse;
import com.cosmate.entity.*;
import com.cosmate.repository.*;
import com.cosmate.service.OrderService;
import com.cosmate.service.ProviderService;
import com.cosmate.service.WalletService;
import com.cosmate.service.VnPayService;
import com.cosmate.service.MomoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@RestController
@RequestMapping("/api/service-orders")
@RequiredArgsConstructor
public class ServiceOrderController {

    private final OrderRepository orderRepository;
    private final OrderServiceBookingRepository orderServiceBookingRepository;
    private final ServiceRepository serviceRepository;
    private final ProviderService providerService;
    private final OrderService orderService;
    private final WalletService walletService;
    private final VnPayService vnPayService;
    private final MomoService momoService;
    private final TransactionRepository transactionRepository;
    private final com.cosmate.repository.UserRepository userRepository;
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

            // check provider
            Provider prov = providerService.getByUserId(currentUserId);
            if (prov == null) return ApiResponse.<OrderResponse>builder().code(403).message("User is not a provider").build();

            // check provider role types via ProviderService or assume provider has role stored elsewhere - we'll check user's authorities
            boolean allowed = hasRole(auth, "PROVIDER_PHOTOGRAPH") || hasRole(auth, "PROVIDER_EVENT_STAFF");
            if (!allowed) return ApiResponse.<OrderResponse>builder().code(403).message("Only photography/event staff providers can create service bookings").build();

            if (req.getServiceId() == null) return ApiResponse.<OrderResponse>builder().code(400).message("serviceId is required").build();
            Optional<Service> sopt = serviceRepository.findById(req.getServiceId());
            if (sopt.isEmpty()) return ApiResponse.<OrderResponse>builder().code(404).message("Service not found").build();
            Service s = sopt.get();

            // provider creating must own the service
            if (!prov.getId().equals(s.getProviderId())) return ApiResponse.<OrderResponse>builder().code(403).message("Provider does not own the service").build();

            // cosplayerId is required in request
            Integer cosplayerId = req.getCosplayerId();
            if (cosplayerId == null) return ApiResponse.<OrderResponse>builder().code(400).message("cosplayerId is required").build();

            LocalDate bookingDate = null;
            try {
                bookingDate = LocalDate.parse(req.getBookingDate());
            } catch (DateTimeParseException ex) {
                return ApiResponse.<OrderResponse>builder().code(400).message("Invalid bookingDate format, expected yyyy-MM-dd").build();
            }

            // compute total amount: rentSlotAmount + depositAmount from Service (if provided)
            java.math.BigDecimal rent = req.getRentSlotAmount() == null ? java.math.BigDecimal.ZERO : req.getRentSlotAmount();
            java.math.BigDecimal deposit = s.getDepositAmount() == null ? java.math.BigDecimal.ZERO : s.getDepositAmount();
            java.math.BigDecimal total = rent.add(deposit);

            // create Order with type RENT_SERVICE and status UNCONFIRM
            Order order = Order.builder()
                    .cosplayerId(cosplayerId)
                    .providerId(prov.getId())
                    .orderType("RENT_SERVICE")
                    .status("UNCONFIRM")
                    .totalAmount(total)
                    .totalDepositAmount(deposit)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();
            order = orderRepository.save(order);

            // create OrderServiceBooking record
            OrderServiceBooking osb = OrderServiceBooking.builder()
                    .orderId(order.getId())
                    .serviceId(s.getId())
                    .bookingDate(bookingDate)
                    .timeSlot(req.getTimeSlot())
                    .numberOfHuman(req.getNumberOfHuman())
                    .depositSlotAmount(deposit)
                    .rentSlotAmount(rent)
                    .build();
            orderServiceBookingRepository.save(osb);

            OrderResponse resp = new OrderResponse();
            resp.setId(order.getId());
            resp.setCosplayerId(order.getCosplayerId());
            resp.setProviderId(order.getProviderId());
            resp.setOrderType(order.getOrderType());
            resp.setStatus(order.getStatus());
            resp.setTotalAmount(order.getTotalAmount());
            resp.setCreatedAt(order.getCreatedAt());

            return ApiResponse.<OrderResponse>builder().result(resp).message("Service booking created; waiting for cosplayer confirmation").build();

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

            Optional<Order> opt = orderRepository.findById(id);
            if (opt.isEmpty()) return ApiResponse.<OrderResponse>builder().code(404).message("Order not found").build();
            Order order = opt.get();

            if (!order.getCosplayerId().equals(currentUserId)) return ApiResponse.<OrderResponse>builder().code(403).message("Order does not belong to user").build();

            if (!"UNCONFIRM".equals(order.getStatus())) return ApiResponse.<OrderResponse>builder().code(400).message("Order is not in UNCONFIRM status").build();

            // transition only: UNCONFIRM -> UNPAID
            order.setStatus("UNPAID");
            orderRepository.save(order);
            try {
                com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                        .user(com.cosmate.entity.User.builder().id(order.getCosplayerId()).build())
                        .type("ORDER_STATUS")
                        .header("Đơn hàng đã được xác nhận")
                        .content("Đơn hàng #" + order.getId() + " đã được xác nhận và chuyển sang UNPAID.")
                        .sendAt(java.time.LocalDateTime.now())
                        .isRead(false)
                        .build();
                notificationService.create(n);
            } catch (Exception ignored) {}

            OrderResponse resp = new OrderResponse();
            resp.setId(order.getId());
            resp.setCosplayerId(order.getCosplayerId());
            resp.setProviderId(order.getProviderId());
            resp.setOrderType(order.getOrderType());
            resp.setStatus(order.getStatus());
            resp.setTotalAmount(order.getTotalAmount());
            resp.setCreatedAt(order.getCreatedAt());

            return ApiResponse.<OrderResponse>builder().result(resp).message("Order confirmed by cosplayer and set to UNPAID").build();

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
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<OrderResponse>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();

            Provider prov = providerService.getByUserId(currentUserId);
            if (prov == null) return ApiResponse.<OrderResponse>builder().code(403).message("User is not a provider").build();

            Optional<Order> opt = orderRepository.findById(id);
            if (opt.isEmpty()) return ApiResponse.<OrderResponse>builder().code(404).message("Order not found").build();
            Order order = opt.get();

            if (!order.getProviderId().equals(prov.getId())) return ApiResponse.<OrderResponse>builder().code(403).message("Provider does not own this order").build();

            if (!"PAID".equals(order.getStatus())) return ApiResponse.<OrderResponse>builder().code(400).message("Order must be in PAID status to set WAITING_SERVICE_DATE").build();

            order.setStatus("WAITING_SERVICE_DATE");
            orderRepository.save(order);
            try {
                com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                        .user(com.cosmate.entity.User.builder().id(order.getCosplayerId()).build())
                        .type("ORDER_STATUS")
                        .header("Đơn hàng đã được lên lịch")
                        .content("Đơn hàng #" + order.getId() + " đã được đặt lịch (WAITING_SERVICE_DATE).")
                        .sendAt(java.time.LocalDateTime.now())
                        .isRead(false)
                        .build();
                notificationService.create(n);
            } catch (Exception ignored) {}

            OrderResponse resp = new OrderResponse();
            resp.setId(order.getId());
            resp.setCosplayerId(order.getCosplayerId());
            resp.setProviderId(order.getProviderId());
            resp.setOrderType(order.getOrderType());
            resp.setStatus(order.getStatus());
            resp.setTotalAmount(order.getTotalAmount());
            resp.setCreatedAt(order.getCreatedAt());

            return ApiResponse.<OrderResponse>builder().result(resp).message("Order set to WAITING_SERVICE_DATE").build();
        } catch (Exception ex) {
            return ApiResponse.<OrderResponse>builder().code(500).message("Failed to set WAITING_SERVICE_DATE: " + ex.getMessage()).build();
        }
    }

    // Endpoint to manually trigger transition to IN_SERVICE for a given order (useful for testing or manual corrections)
    @PostMapping("/{id}/start-service-now")
    public ApiResponse<OrderResponse> startServiceNow(@PathVariable Integer id) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<OrderResponse>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();

            Provider prov = providerService.getByUserId(currentUserId);
            if (prov == null) return ApiResponse.<OrderResponse>builder().code(403).message("User is not a provider").build();

            Optional<Order> opt = orderRepository.findById(id);
            if (opt.isEmpty()) return ApiResponse.<OrderResponse>builder().code(404).message("Order not found").build();
            Order order = opt.get();

            if (!order.getProviderId().equals(prov.getId())) return ApiResponse.<OrderResponse>builder().code(403).message("Provider does not own this order").build();

            if (!"WAITING_SERVICE_DATE".equals(order.getStatus())) return ApiResponse.<OrderResponse>builder().code(400).message("Order must be in WAITING_SERVICE_DATE status to start service").build();

            // find booking and ensure bookingDate is today or earlier
            java.util.List<OrderServiceBooking> bookings = orderServiceBookingRepository.findByOrderId(order.getId());
            if (bookings.isEmpty()) return ApiResponse.<OrderResponse>builder().code(400).message("No service booking found for order").build();
            OrderServiceBooking osb = bookings.get(0);
            LocalDate today = LocalDate.now();
            if (osb.getBookingDate() != null && osb.getBookingDate().isAfter(today)) {
                return ApiResponse.<OrderResponse>builder().code(400).message("Booking date is in the future; cannot start service yet").build();
            }

            order.setStatus("IN_SERVICE");
            orderRepository.save(order);
            try {
                com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                        .user(com.cosmate.entity.User.builder().id(order.getCosplayerId()).build())
                        .type("ORDER_STATUS")
                        .header("Đơn hàng đang được thực hiện")
                        .content("Đơn hàng #" + order.getId() + " đã bắt đầu (IN_SERVICE).")
                        .sendAt(java.time.LocalDateTime.now())
                        .isRead(false)
                        .build();
                notificationService.create(n);
            } catch (Exception ignored) {}

            OrderResponse resp = new OrderResponse();
            resp.setId(order.getId());
            resp.setCosplayerId(order.getCosplayerId());
            resp.setProviderId(order.getProviderId());
            resp.setOrderType(order.getOrderType());
            resp.setStatus(order.getStatus());
            resp.setTotalAmount(order.getTotalAmount());
            resp.setCreatedAt(order.getCreatedAt());

            return ApiResponse.<OrderResponse>builder().result(resp).message("Order set to IN_SERVICE").build();
        } catch (Exception ex) {
            return ApiResponse.<OrderResponse>builder().code(500).message("Failed to start service: " + ex.getMessage()).build();
        }
    }

    // Provider marks an IN_SERVICE order as COMPLETED
    @PostMapping("/{id}/provider-complete")
    public ApiResponse<OrderResponse> providerCompleteService(@PathVariable Integer id) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<OrderResponse>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();

            Provider prov = providerService.getByUserId(currentUserId);
            if (prov == null) return ApiResponse.<OrderResponse>builder().code(403).message("User is not a provider").build();

            Optional<Order> opt = orderRepository.findById(id);
            if (opt.isEmpty()) return ApiResponse.<OrderResponse>builder().code(404).message("Order not found").build();
            Order order = opt.get();

            if (!order.getProviderId().equals(prov.getId())) return ApiResponse.<OrderResponse>builder().code(403).message("Provider does not own this order").build();

            if (!"IN_SERVICE".equals(order.getStatus())) return ApiResponse.<OrderResponse>builder().code(400).message("Order must be IN_SERVICE to be completed").build();

            // set to COMPLETED
            order.setStatus("COMPLETED");
            orderRepository.save(order);
            try {
                com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                        .user(com.cosmate.entity.User.builder().id(order.getCosplayerId()).build())
                        .type("ORDER_STATUS")
                        .header("Dịch vụ hoàn tất")
                        .content("Đơn hàng #" + order.getId() + " dịch vụ đã hoàn tất (COMPLETED).")
                        .sendAt(java.time.LocalDateTime.now())
                        .isRead(false)
                        .build();
                notificationService.create(n);
            } catch (Exception ignored) {}

            // Transfer money to provider's wallet when service completes
            try {
                // provider.userId is the user account id for the provider
                Integer providerUserId = prov.getUserId();
                java.util.Optional<com.cosmate.entity.Wallet> wopt = walletService.getByUserId(providerUserId);
                if (wopt.isPresent()) {
                    com.cosmate.entity.Wallet wallet = wopt.get();
                    java.math.BigDecimal amount = order.getTotalAmount() == null ? java.math.BigDecimal.ZERO : order.getTotalAmount();
                    walletService.credit(wallet, amount, "Payout for completed order", "ORDER_PAYOUT:" + order.getId(), null, order);
                } else {
                    // wallet not found - optional: create wallet and credit
                    java.util.Optional<com.cosmate.entity.User> providerUserOpt = userRepository.findById(providerUserId);
                    if (providerUserOpt.isPresent()) {
                        walletService.createForUser(providerUserOpt.get());
                        java.util.Optional<com.cosmate.entity.Wallet> wopt2 = walletService.getByUserId(providerUserId);
                        if (wopt2.isPresent()) {
                            com.cosmate.entity.Wallet wallet = wopt2.get();
                            java.math.BigDecimal amount = order.getTotalAmount() == null ? java.math.BigDecimal.ZERO : order.getTotalAmount();
                            walletService.credit(wallet, amount, "Payout for completed order", "ORDER_PAYOUT:" + order.getId(), null, order);
                        }
                    }
                }
            } catch (Exception ex) {
                // Log but do not fail the API call
                // If logging framework not available here, swallow silently or consider notifying admin
            }

            OrderResponse resp = new OrderResponse();
            resp.setId(order.getId());
            resp.setCosplayerId(order.getCosplayerId());
            resp.setProviderId(order.getProviderId());
            resp.setOrderType(order.getOrderType());
            resp.setStatus(order.getStatus());
            resp.setTotalAmount(order.getTotalAmount());
            resp.setCreatedAt(order.getCreatedAt());

            return ApiResponse.<OrderResponse>builder().result(resp).message("Order marked as COMPLETED").build();
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

            Optional<Order> opt = orderRepository.findById(id);
            if (opt.isEmpty()) return ApiResponse.<OrderResponse>builder().code(404).message("Order not found").build();
            Order order = opt.get();

            // Only cosplayer (owner) or provider (owner) can cancel
            boolean isCosplayer = order.getCosplayerId() != null && order.getCosplayerId().equals(currentUserId);
            Provider prov = providerService.getByUserId(currentUserId);
            boolean isProviderOwner = prov != null && order.getProviderId() != null && order.getProviderId().equals(prov.getId());
            if (!isCosplayer && !isProviderOwner) return ApiResponse.<OrderResponse>builder().code(403).message("No permission to cancel this order").build();

            String status = order.getStatus();
            // Allowed statuses to cancel: UNCONFIRM, UNPAID, PAID, WAITING_SERVICE_DATE
            if (status == null) status = "";
            if (!(status.equals("UNCONFIRM") || status.equals("UNPAID") || status.equals("PAID") || status.equals("WAITING_SERVICE_DATE"))) {
                return ApiResponse.<OrderResponse>builder().code(400).message("Order cannot be cancelled in its current status: " + status).build();
            }

            // If PAID -> refund full totalAmount to cosplayer's wallet
            if ("PAID".equals(status)) {
                if (order.getCosplayerId() == null) return ApiResponse.<OrderResponse>builder().code(400).message("Cosplayer info missing on order; cannot refund").build();
                java.util.Optional<com.cosmate.entity.Wallet> wopt = walletService.getByUserId(order.getCosplayerId());
                if (wopt.isEmpty()) return ApiResponse.<OrderResponse>builder().code(500).message("Wallet for cosplayer not found; refund failed").build();
                com.cosmate.entity.Wallet wallet = wopt.get();
                java.math.BigDecimal amount = order.getTotalAmount() == null ? java.math.BigDecimal.ZERO : order.getTotalAmount();
                // credit wallet and record transaction via WalletService.credit
                walletService.credit(wallet, amount, "Refund for cancelled order", "ORDER_REFUND:" + order.getId(), null, order);
            }

            order.setStatus("CANCELLED");
            orderRepository.save(order);
            try {
                com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                        .user(com.cosmate.entity.User.builder().id(order.getCosplayerId()).build())
                        .type("ORDER_STATUS")
                        .header("Đơn hàng bị hủy")
                        .content("Đơn hàng #" + order.getId() + " đã bị hủy.")
                        .sendAt(java.time.LocalDateTime.now())
                        .isRead(false)
                        .build();
                notificationService.create(n);
            } catch (Exception ignored) {}

            OrderResponse resp = new OrderResponse();
            resp.setId(order.getId());
            resp.setCosplayerId(order.getCosplayerId());
            resp.setProviderId(order.getProviderId());
            resp.setOrderType(order.getOrderType());
            resp.setStatus(order.getStatus());
            resp.setTotalAmount(order.getTotalAmount());
            resp.setCreatedAt(order.getCreatedAt());

            return ApiResponse.<OrderResponse>builder().result(resp).message("Order cancelled").build();

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

            Provider prov = providerService.getByUserId(currentUserId);
            if (prov == null) return ApiResponse.<java.util.List<com.cosmate.dto.response.ServiceOrderItemResponse>>builder().code(403).message("User is not a provider").build();

            java.util.List<String> statusList = null;
            if (statuses != null && !statuses.trim().isEmpty()) {
                statusList = java.util.Arrays.stream(statuses.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
            }

            java.util.List<Order> orders;
            if (statusList == null) {
                orders = orderRepository.findByProviderIdOrderByCreatedAtDesc(prov.getId());
            } else {
                orders = orderRepository.findByProviderIdAndStatusInOrderByCreatedAtDesc(prov.getId(), statusList);
            }

            // filter only RENT_SERVICE
            java.util.List<Order> serviceOrders = orders.stream().filter(o -> "RENT_SERVICE".equals(o.getOrderType())).toList();

            java.util.List<com.cosmate.dto.response.ServiceOrderItemResponse> respList = new java.util.ArrayList<>();
            for (Order o : serviceOrders) {
                com.cosmate.dto.response.ServiceOrderItemResponse item = new com.cosmate.dto.response.ServiceOrderItemResponse();
                item.setId(o.getId());
                item.setCosplayerId(o.getCosplayerId());
                item.setProviderId(o.getProviderId());
                item.setOrderType(o.getOrderType());
                item.setStatus(o.getStatus());
                item.setTotalAmount(o.getTotalAmount());
                item.setCreatedAt(o.getCreatedAt());
                item.setBookings(orderServiceBookingRepository.findByOrderId(o.getId()));
                respList.add(item);
            }

            return ApiResponse.<java.util.List<com.cosmate.dto.response.ServiceOrderItemResponse>>builder().result(respList).build();
        } catch (Exception ex) {
            return ApiResponse.<java.util.List<com.cosmate.dto.response.ServiceOrderItemResponse>>builder().code(500).message("Failed to list provider service orders: " + ex.getMessage()).build();
        }
    }
}
