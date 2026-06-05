package com.cosmate;

import com.cosmate.entity.Order;
import com.cosmate.entity.OrderDetail;
import com.cosmate.entity.OrderDetailExtend;
import com.cosmate.entity.OrderServiceBooking;
import com.cosmate.repository.OrderDetailExtendRepository;
import com.cosmate.repository.OrderDetailRepository;
import com.cosmate.repository.OrderRepository;
import com.cosmate.repository.OrderServiceBookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceOrderScheduler {

    private final OrderServiceBookingRepository orderServiceBookingRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final OrderDetailExtendRepository orderDetailExtendRepository;
    private final com.cosmate.repository.TransactionRepository transactionRepository;
    private final com.cosmate.repository.TokenPurchaseHistoryRepository tokenPurchaseHistoryRepository;
    private final com.cosmate.repository.ProviderSubscriptionRepository providerSubscriptionRepository;
    private final com.cosmate.repository.UserRepository userRepository;
    private final com.cosmate.service.NotificationService notificationService;
    private final com.cosmate.service.WalletService walletService;
    private final com.cosmate.service.ProviderService providerService;
    private final com.cosmate.repository.CostumeRepository costumeRepository;
    private final com.cosmate.repository.OrderImageRepository orderImageRepository;
    private final com.cosmate.repository.OrderTrackingRepository orderTrackingRepository;
    private final com.cosmate.repository.WishlistRepository wishlistRepository;

    // run every 10 minutes to pick up bookings that should start today
    @Scheduled(fixedDelayString = "PT10M")
    public void startServiceOnBookingDate() {
        try {
            LocalDate today = LocalDate.now();
            List<OrderServiceBooking> list = orderServiceBookingRepository.findAll();
            for (OrderServiceBooking osb : list) {
                if (osb.getBookingDate() == null) continue;
                if (!osb.getBookingDate().isEqual(today)) continue;
                Integer orderId = osb.getOrderId();
                Order order = orderRepository.findById(orderId).orElse(null);
                if (order == null) continue;
                if (!"WAITING_SERVICE_DATE".equals(order.getStatus())) continue;
                order.setStatus("IN_SERVICE");
                orderRepository.save(order);
                // notify user that order moved to IN_SERVICE
                try {
                    com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                            .user(com.cosmate.entity.User.builder().id(order.getCosplayerId()).build())
                            .type("ORDER_STATUS")
                            .header("Đơn hàng đang được thực hiện")
                            .content("Đơn hàng #" + orderId + " đã bắt đầu (IN_SERVICE).")
                            .sendAt(java.time.LocalDateTime.now())
                            .isRead(false)
                            .build();
                    notificationService.create(n);
                } catch (Exception ignored) {}
                log.info("Auto-updated order {} to IN_SERVICE for booking date {}", orderId, today);
            }
        } catch (Exception ex) {
            log.error("Error in ServiceOrderScheduler.startServiceOnBookingDate: {}", ex.getMessage(), ex);
        }
    }

    // run every 1 minute to mark old pending transactions as FAILED
    @Scheduled(fixedDelayString = "PT5M")
    public void markOldPendingTransactionsFailed() {
        try {
            java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusMinutes(16);
            java.util.List<com.cosmate.entity.Transaction> pending = transactionRepository.findByStatus("PENDING");
            for (com.cosmate.entity.Transaction t : pending) {
                if (t.getCreatedAt() == null) continue;
                if (t.getCreatedAt().isBefore(cutoff) || t.getCreatedAt().isEqual(cutoff)) {
                    t.setStatus("FAILED");
                    transactionRepository.save(t);
                    // try notify user via wallet -> wallet.user
                    try {
                        com.cosmate.entity.Wallet w = t.getWallet();
                        if (w != null && w.getUser() != null && w.getUser().getId() != null) {
                            com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                                    .user(com.cosmate.entity.User.builder().id(w.getUser().getId()).build())
                                    .type("TRANSACTION")
                                    .header("Giao dịch thất bại")
                                    .content("Giao dịch " + t.getType() + " (id=" + t.getId() + ") đã chuyển sang FAILED do quá thời gian.")
                                    .sendAt(java.time.LocalDateTime.now())
                                    .isRead(false)
                                    .build();
                            notificationService.create(n);
                        }
                    } catch (Exception ignored) {}
                    log.info("Marked transaction {} as FAILED due to timeout", t.getId());
                }
            }
        } catch (Exception ex) {
            log.error("Error in ServiceOrderScheduler.markOldPendingTransactionsFailed: {}", ex.getMessage(), ex);
        }
    }

    // run every 1 minute to mark old pending token purchase histories as FAILED
    @Scheduled(fixedDelayString = "PT1M")
    public void markOldPendingTokenPurchasesFailed() {
        try {
            java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusMinutes(20);
            java.util.List<com.cosmate.entity.TokenPurchaseHistory> pending = tokenPurchaseHistoryRepository.findByStatusAndPurchaseDateBefore("PENDING", cutoff);
            for (com.cosmate.entity.TokenPurchaseHistory h : pending) {
                h.setStatus("FAILED");
                tokenPurchaseHistoryRepository.save(h);
                try {
                    com.cosmate.entity.User u = h.getUser();
                    if (u != null && u.getId() != null) {
                        com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                                .user(com.cosmate.entity.User.builder().id(u.getId()).build())
                                .type("TOKEN_PURCHASE")
                                .header("Giao dịch mua token thất bại")
                                .content("Đơn mua token (id=" + h.getId() + ") đã chuyển sang FAILED do quá thời gian.")
                                .sendAt(java.time.LocalDateTime.now())
                                .isRead(false)
                                .build();
                        notificationService.create(n);
                    }
                } catch (Exception ignored) {}
                log.info("Marked token purchase history {} as FAILED due to timeout", h.getId());
            }
        } catch (Exception ex) {
            log.error("Error in ServiceOrderScheduler.markOldPendingTokenPurchasesFailed: {}", ex.getMessage(), ex);
        }
    }

    // run hourly to grant monthly tokens for active provider subscriptions
    @Scheduled(fixedDelayString = "PT1H")
    public void grantMonthlyTokensForProviderSubscriptions() {
        try {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.util.List<com.cosmate.entity.ProviderSubscription> list = providerSubscriptionRepository.findByStatusAndNextTokenGrantAtBefore("ACTIVE", now);
            for (com.cosmate.entity.ProviderSubscription ps : list) {
                try {
                    if (ps.getMonthlyToken() == null || ps.getMonthlyToken() <= 0) continue;
                    if (ps.getEndDate() != null && (ps.getNextTokenGrantAt() == null || !ps.getNextTokenGrantAt().isBefore(ps.getEndDate()))) continue;
                    com.cosmate.entity.Provider prov = ps.getProvider();
                    if (prov == null || prov.getUserId() == null) continue;
                    java.util.Optional<com.cosmate.entity.User> uopt = userRepository.findById(prov.getUserId());
                    if (uopt.isPresent()) {
                        com.cosmate.entity.User u = uopt.get();
                        Integer curr = u.getNumberOfToken() == null ? 0 : u.getNumberOfToken();
                        u.setNumberOfToken(curr + ps.getMonthlyToken());
                        userRepository.save(u);
                        // notify provider
                        try {
                            com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                                    .user(com.cosmate.entity.User.builder().id(u.getId()).build())
                                    .type("TOKEN_GRANT")
                                    .header("Đã nhận token hàng tháng")
                                    .content("Bạn đã nhận " + ps.getMonthlyToken() + " token cho gói đăng ký '" + (ps.getName() == null ? "" : ps.getName()) + "'.")
                                    .sendAt(java.time.LocalDateTime.now())
                                    .isRead(false)
                                    .build();
                            notificationService.create(n);
                        } catch (Exception ignored) {}
                    }
                    // advance nextTokenGrantAt by one month
                    java.time.LocalDateTime next = ps.getNextTokenGrantAt() == null ? ps.getStartDate().plusMonths(1) : ps.getNextTokenGrantAt().plusMonths(1);
                    ps.setNextTokenGrantAt(next);
                    providerSubscriptionRepository.save(ps);
                    log.info("Granted monthly tokens for provider subscription {}: +{} tokens", ps.getId(), ps.getMonthlyToken());
                } catch (Exception e) {
                    log.error("Error granting monthly tokens for provider subscription {}: {}", ps.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception ex) {
            log.error("Error in ServiceOrderScheduler.grantMonthlyTokensForProviderSubscriptions: {}", ex.getMessage(), ex);
        }
    }
    // run every 5 minutes to move orders from IN_USE to EXTENDING when an extend exists
    @Scheduled(fixedDelayString = "PT5M")
    public void moveInUseOrdersToExtendingIfHasExtend() {
        try {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.util.List<Order> inUse = orderRepository.findByStatus("IN_USE");
            for (Order order : inUse) {
                boolean shouldExtend = false;
                java.util.List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
                if (details == null || details.isEmpty()) continue;
                for (OrderDetail d : details) {
                    if (d.getRentEnd() == null) continue;
                    // only consider details whose rentEnd is reached or passed
                    if (d.getRentEnd().isAfter(now)) continue;
                    java.util.List<OrderDetailExtend> exts = orderDetailExtendRepository.findByOrderDetailId(d.getId());
                    if (exts == null || exts.isEmpty()) continue;
                    for (OrderDetailExtend e : exts) {
                        // any extend that is not cancelled should trigger transition
                        if (!"CANCELLED".equalsIgnoreCase(e.getPaymentStatus())) {
                            shouldExtend = true;
                            break;
                        }
                    }
                    if (shouldExtend) break;
                }
                if (shouldExtend) {
                    order.setStatus("EXTENDING");
                    orderRepository.save(order);
                    try {
                        com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                                .user(com.cosmate.entity.User.builder().id(order.getCosplayerId()).build())
                                .type("ORDER_STATUS")
                                .header("Đơn hàng đang được gia hạn")
                                .content("Đơn hàng #" + order.getId() + " đang chuyển sang EXTENDING do có yêu cầu gia hạn.")
                                .sendAt(java.time.LocalDateTime.now())
                                .isRead(false)
                                .build();
                        notificationService.create(n);
                    } catch (Exception ignored) {}
                    log.info("Auto-updated order {} to EXTENDING due to extend request", order.getId());
                }
            }
        } catch (Exception ex) {
            log.error("Error in ServiceOrderScheduler.moveInUseOrdersToExtendingIfHasExtend: {}", ex.getMessage(), ex);
        }
    }

    // run hourly to cancel UNPAID orders older than 1 day (no money handling)
    @Scheduled(fixedDelayString = "PT1H")
    public void cancelOldUnpaidOrders() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<Order> unpaid = orderRepository.findByStatus("UNPAID");
            if (unpaid == null || unpaid.isEmpty()) return;
            for (Order o : unpaid) {
                try {
                    if (o == null || o.getCreatedAt() == null) continue;
                    if (o.getCreatedAt().isAfter(now.minusDays(1))) continue;
                    // cancel without financial handling
                    o.setStatus("CANCELLED");
                    orderRepository.save(o);
                    try {
                        com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                                .user(com.cosmate.entity.User.builder().id(o.getCosplayerId()).build())
                                .type("ORDER_STATUS")
                                .header("Đơn hàng bị hủy do chưa thanh toán")
                                .content("Đơn hàng #" + o.getId() + " đã bị hủy vì chưa được thanh toán trong vòng 24 giờ.")
                                .sendAt(java.time.LocalDateTime.now())
                                .isRead(false)
                                .build();
                        notificationService.create(n);
                    } catch (Exception ignored) {}
                    // set costumes (if any) back to AVAILABLE
                    try {
                        List<OrderDetail> details = orderDetailRepository.findByOrderId(o.getId());
                        if (details != null && !details.isEmpty()) {
                            for (OrderDetail d : details) {
                                if (d == null || d.getCostumeId() == null) continue;
                                com.cosmate.entity.Costume c = costumeRepository.findById(d.getCostumeId()).orElse(null);
                                if (c != null) {
                                    c.setStatus("AVAILABLE");
                                    costumeRepository.save(c);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                    log.info("Cancelled unpaid order {} due to timeout", o.getId());
                } catch (Exception e) {
                    log.error("Error cancelling unpaid order {}: {}", o == null ? null : o.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception ex) {
            log.error("Error in ServiceOrderScheduler.cancelOldUnpaidOrders: {}", ex.getMessage(), ex);
        }
    }

    // run hourly to cancel PAID orders if provider didn't move to PREPARING within 3 days from creation
    @Scheduled(fixedDelayString = "PT1H")
    public void cancelPaidOrdersUnconfirmedByProvider() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<Order> paid = orderRepository.findByStatus("PAID");
            if (paid == null || paid.isEmpty()) return;
            for (Order o : paid) {
                try {
                    if (o == null || o.getCreatedAt() == null) continue;
                    if (o.getCreatedAt().isAfter(now.minusDays(3))) continue;
                    // Provider did not move to PREPARING in 3 days -> cancel and refund full amount to cosplayer
                    java.math.BigDecimal total = o.getTotalAmount() == null ? java.math.BigDecimal.ZERO : o.getTotalAmount();

                    // refund full amount to cosplayer
                    try {
                        com.cosmate.entity.User cosUser = com.cosmate.entity.User.builder().id(o.getCosplayerId()).build();
                        com.cosmate.entity.Wallet cosWallet = walletService.createForUser(cosUser);
                        if (total.compareTo(java.math.BigDecimal.ZERO) > 0) {
                            walletService.credit(cosWallet, total, "Tiền refund của đơn hàng do nhà cung cấp không xác nhận đơn", "AUTO_REFUND_NO_PREPARE:" + o.getId(), null, o);
                        }
                    } catch (Exception ignored) {}

                    // set costumes back to AVAILABLE
                    try {
                        List<OrderDetail> details = orderDetailRepository.findByOrderId(o.getId());
                        if (details != null && !details.isEmpty()) {
                            for (OrderDetail d : details) {
                                if (d == null || d.getCostumeId() == null) continue;
                                com.cosmate.entity.Costume c = costumeRepository.findById(d.getCostumeId()).orElse(null);
                                if (c != null) {
                                    c.setStatus("AVAILABLE");
                                    costumeRepository.save(c);
                                }
                            }
                        }
                    } catch (Exception ignored) {}

                    o.setStatus("CANCELLED");
                    orderRepository.save(o);

                    // notify both parties
                    try {
                        com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                                .user(com.cosmate.entity.User.builder().id(o.getCosplayerId()).build())
                                .type("ORDER_STATUS")
                                .header("Đơn hàng bị hủy - nhà cung cấp không xác nhận")
                                .content("Đơn hàng #" + o.getId() + " đã bị hủy vì nhà cung cấp không xác nhận trong vòng 3 ngày. Số tiền đã thanh toán sẽ được hoàn trả.")
                                .sendAt(java.time.LocalDateTime.now())
                                .isRead(false)
                                .build();
                        notificationService.create(n);
                    } catch (Exception ignored) {}

                    try {
                        Integer providerUserId = null;
                        try { com.cosmate.entity.Provider provEntity = providerService.getById(o.getProviderId()); if (provEntity != null) providerUserId = provEntity.getUserId(); } catch (Exception ignored) { providerUserId = null; }
                        if (providerUserId != null) {
                            com.cosmate.entity.Notification pn = com.cosmate.entity.Notification.builder()
                                    .user(com.cosmate.entity.User.builder().id(providerUserId).build())
                                    .type("ORDER_STATUS")
                                    .header("Đơn hàng bị hủy")
                                    .content("Đơn hàng #" + o.getId() + " đã bị hủy vì bạn chưa xác nhận chuẩn bị trong vòng 3 ngày kể từ khi đặt.")
                                    .sendAt(java.time.LocalDateTime.now())
                                    .isRead(false)
                                    .build();
                            notificationService.create(pn);
                        }
                    } catch (Exception ignored) {}

                    log.info("Auto-cancelled PAID order {} because provider did not confirm preparing", o.getId());
                } catch (Exception e) {
                    log.error("Error auto-cancelling paid order {}: {}", o == null ? null : o.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception ex) {
            log.error("Error in ServiceOrderScheduler.cancelPaidOrdersUnconfirmedByProvider: {}", ex.getMessage(), ex);
        }
    }

    // run every 5 minutes to move DELIVERING_OUT orders to IN_USE when rentStart has arrived
    @Scheduled(fixedDelayString = "PT5M")
    public void moveDeliveringOutToInUseWhenStart() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<Order> delivering = orderRepository.findByStatus("DELIVERING_OUT");
            if (delivering == null || delivering.isEmpty()) return;
            for (Order o : delivering) {
                try {
                    List<OrderDetail> details = orderDetailRepository.findByOrderId(o.getId());
                    if (details == null || details.isEmpty()) continue;
                    boolean shouldBecomeInUse = false;
                    for (OrderDetail d : details) {
                        if (d == null || d.getRentStart() == null) continue;
                        if (!d.getRentStart().isAfter(now)) { // rentStart <= now
                            shouldBecomeInUse = true;
                            break;
                        }
                    }
                    if (shouldBecomeInUse) {
                        o.setStatus("IN_USE");
                        orderRepository.save(o);
                        try {
                            com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                                    .user(com.cosmate.entity.User.builder().id(o.getCosplayerId()).build())
                                    .type("ORDER_STATUS")
                                    .header("Đơn hàng đang trong quá trình sử dụng")
                                    .content("Đơn hàng #" + o.getId() + " đã chuyển sang IN_USE vì đến thời gian bắt đầu sử dụng.")
                                    .sendAt(java.time.LocalDateTime.now())
                                    .isRead(false)
                                    .build();
                            notificationService.create(n);
                        } catch (Exception ignored) {}
                        log.info("Auto-updated order {} from DELIVERING_OUT to IN_USE due to rentStart", o.getId());
                    }
                } catch (Exception e) {
                    log.error("Error moving delivering order {} to IN_USE: {}", o == null ? null : o.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception ex) {
            log.error("Error in ServiceOrderScheduler.moveDeliveringOutToInUseWhenStart: {}", ex.getMessage(), ex);
        }
    }

    // run hourly to check for overdue rent-costume orders and handle warnings / auto-complete
    @Scheduled(fixedDelayString = "PT1H")
    public void checkOverdueRentOrders() {
        try {
            LocalDateTime now = LocalDateTime.now();
            // consider both IN_USE and EXTENDING orders
            List<Order> inUse = orderRepository.findByStatus("IN_USE");
            List<Order> extending = orderRepository.findByStatus("EXTENDING");
            List<Order> candidates = new java.util.ArrayList<>();
            if (inUse != null) candidates.addAll(inUse);
            if (extending != null) candidates.addAll(extending);

            for (Order order : candidates) {
                try {
                    if (order == null) continue;
                    List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
                    if (details == null || details.isEmpty()) continue;

                    // compute the latest effective end date among details (consider paid extends)
                    LocalDateTime latestEnd = null;
                    for (OrderDetail d : details) {
                        if (d == null) continue;
                        LocalDateTime eff = d.getRentEnd();
                        // check for paid extension(s)
                        try {
                            List<OrderDetailExtend> exts = orderDetailExtendRepository.findByOrderDetailId(d.getId());
                            if (exts != null && !exts.isEmpty()) {
                                // find the last paid extend's new_return_date if any
                                for (OrderDetailExtend e : exts) {
                                    if (e != null && "PAID".equalsIgnoreCase(e.getPaymentStatus())) {
                                        if (e.getNewReturnDate() != null) eff = e.getNewReturnDate();
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                        if (eff == null) continue;
                        if (latestEnd == null || eff.isAfter(latestEnd)) latestEnd = eff;
                    }
                    if (latestEnd == null) continue;

                    long daysOverdue = ChronoUnit.DAYS.between(latestEnd.toLocalDate(), now.toLocalDate());
                    if (daysOverdue <= 0) continue;

                    // if not yet marked OVERDUE, set it now
                    if (!"OVERDUE".equals(order.getStatus())) {
                        order.setStatus("OVERDUE");
                        orderRepository.save(order);
                        try {
                            com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                                    .user(com.cosmate.entity.User.builder().id(order.getCosplayerId()).build())
                                    .type("ORDER_STATUS")
                                    .header("Đơn hàng quá hạn")
                                    .content("Đơn hàng #" + order.getId() + " đã quá hạn trả hàng (từ " + latestEnd.toLocalDate().toString() + "). Vui lòng trả hàng ngay để tránh mất tiền cọc.")
                                    .sendAt(java.time.LocalDateTime.now())
                                    .isRead(false)
                                    .build();
                            notificationService.create(n);
                        } catch (Exception ignored) {}
                    }

                    // send daily warnings for days 1..7
                    if (daysOverdue >= 1 && daysOverdue <= 7) {
                        java.math.BigDecimal depositTotalPreview = java.math.BigDecimal.ZERO;
                        try {
                            for (OrderDetail d2 : details) {
                                java.math.BigDecimal dep = d2.getDepositAmount() == null ? java.math.BigDecimal.ZERO : d2.getDepositAmount();
                                depositTotalPreview = depositTotalPreview.add(dep);
                            }
                            if ((depositTotalPreview == null || depositTotalPreview.compareTo(java.math.BigDecimal.ZERO) == 0) && order.getTotalDepositAmount() != null) {
                                depositTotalPreview = order.getTotalDepositAmount();
                            }
                        } catch (Exception ignored) {}

                        String content = "Đơn #" + order.getId() + " đã quá hạn từ " + latestEnd.toLocalDate().toString()
                                + ". Nếu không trả trong vòng 7 ngày, bạn sẽ mất tiền cọc: " + depositTotalPreview
                                + ". Tài khoản của bạn sẽ bị xử lý theo quy định nền tảng (bị ban tài khoản).";
                        try {
                            com.cosmate.entity.Notification warn = com.cosmate.entity.Notification.builder()
                                    .user(com.cosmate.entity.User.builder().id(order.getCosplayerId()).build())
                                    .type("ORDER_WARNING")
                                    .header("Cảnh báo: Đơn hàng quá hạn")
                                    .content(content)
                                    .sendAt(java.time.LocalDateTime.now())
                                    .isRead(false)
                                    .build();
                            notificationService.create(warn);
                        } catch (Exception ignored) {}
                        continue; // skip auto-complete until day 8
                    }

                    // on day 8 or later, auto-complete, transfer deposit to provider and ban cosplayer
                    if (daysOverdue >= 8) {
                        try {
                            // compute totals similar to OrderServiceImpl.completeOrder
                            java.math.BigDecimal total = order.getTotalAmount() == null ? java.math.BigDecimal.ZERO : order.getTotalAmount();
                            java.math.BigDecimal depositTotal = java.math.BigDecimal.ZERO;
                            for (OrderDetail d : details) {
                                try { java.math.BigDecimal dep = d.getDepositAmount() == null ? java.math.BigDecimal.ZERO : d.getDepositAmount(); depositTotal = depositTotal.add(dep); } catch (Exception ignored) {}
                            }
                            if ((depositTotal == null || depositTotal.compareTo(java.math.BigDecimal.ZERO) == 0) && order.getTotalDepositAmount() != null) {
                                depositTotal = order.getTotalDepositAmount();
                            }

                            java.math.BigDecimal providerShare = total.subtract(depositTotal);
                            // Add any paid extension amounts
                            java.math.BigDecimal extendTotalPaid = java.math.BigDecimal.ZERO;
                            for (OrderDetail d : details) {
                                try {
                                    List<OrderDetailExtend> exts = orderDetailExtendRepository.findByOrderDetailId(d.getId());
                                    if (exts != null && !exts.isEmpty()) {
                                        for (OrderDetailExtend ex : exts) {
                                            if (ex != null && "PAID".equalsIgnoreCase(ex.getPaymentStatus())) {
                                                java.math.BigDecimal p = ex.getExtendPrice() == null ? java.math.BigDecimal.ZERO : ex.getExtendPrice();
                                                extendTotalPaid = extendTotalPaid.add(p);
                                            }
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                            providerShare = providerShare.add(extendTotalPaid);

                            // credit provider: first providerShare, then depositTotal (forfeit)
                            Integer providerUserId = null;
                            try { com.cosmate.entity.Provider provEntity = providerService.getById(order.getProviderId()); if (provEntity != null) providerUserId = provEntity.getUserId(); } catch (Exception ignored) { providerUserId = null; }

                            if (providerUserId != null) {
                                java.util.Optional<com.cosmate.entity.Wallet> wopt = walletService.getByUserId(providerUserId);
                                com.cosmate.entity.Wallet provWallet = null;
                                if (wopt.isPresent()) provWallet = wopt.get();
                                else {
                                    java.util.Optional<com.cosmate.entity.User> providerUserOpt = userRepository.findById(providerUserId);
                                    if (providerUserOpt.isPresent()) provWallet = walletService.createForUser(providerUserOpt.get());
                                }
                                if (provWallet != null) {
                                    if (providerShare != null && providerShare.compareTo(java.math.BigDecimal.ZERO) > 0) {
                                        walletService.credit(provWallet, providerShare, "Provider payout on overdue auto-complete", "PROVIDER_PAYOUT_OVERDUE:" + order.getId(), null, order);
                                    }
                                    if (depositTotal != null && depositTotal.compareTo(java.math.BigDecimal.ZERO) > 0) {
                                        walletService.credit(provWallet, depositTotal, "Forfeited deposit due to overdue", "DEPOSIT_FORFEIT:" + order.getId(), null, order);
                                    }
                                }
                            }

                            // set costumes to AVAILABLE and increment completed count
                            try {
                                for (OrderDetail d : details) {
                                    if (d.getCostumeId() == null) continue;
                                    com.cosmate.entity.Costume c = costumeRepository.findById(d.getCostumeId()).orElse(null);
                                    if (c != null) {
                                        Integer crc = c.getCompletedRentCount();
                                        if (crc == null || crc == 0) c.setCompletedRentCount(1); else c.setCompletedRentCount(crc + 1);
                                        c.setStatus("AVAILABLE");
                                        costumeRepository.save(c);
                                        try {
                                            java.util.List<com.cosmate.entity.WishlistCostume> watchers = wishlistRepository.findAllByCostumeId(c.getId());
                                            if (watchers != null && !watchers.isEmpty()) {
                                                for (com.cosmate.entity.WishlistCostume w : watchers) {
                                                    try {
                                                        com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                                                                .user(com.cosmate.entity.User.builder().id(w.getUserId()).build())
                                                                .type("WISHLIST_NOTIFY")
                                                                .header("Bộ đồ bạn quan tâm đã có sẵn")
                                                                .content("Bộ đồ '" + c.getName() + "' hiện đã có sẵn để thuê.")
                                                                .sendAt(java.time.LocalDateTime.now())
                                                                .isRead(false)
                                                                .build();
                                                        notificationService.create(n);
                                                    } catch (Exception ignored) {}
                                                }
                                            }
                                        } catch (Exception ignored) {}
                                    }
                                }
                            } catch (Exception ignored) {}

                            order.setStatus("COMPLETED");
                            orderRepository.save(order);

                            // notify provider and cosplayer
                            try {
                                if (providerUserId != null) {
                                    com.cosmate.entity.Notification pn = com.cosmate.entity.Notification.builder()
                                            .user(com.cosmate.entity.User.builder().id(providerUserId).build())
                                            .type("ORDER_STATUS")
                                            .header("Đã nhận tiền cọc do quá hạn")
                                            .content("Bạn đã nhận được tiền cọc của đơn hàng #" + order.getId() + " do khách quá hạn trả hàng.")
                                            .sendAt(java.time.LocalDateTime.now())
                                            .isRead(false)
                                            .build();
                                    notificationService.create(pn);
                                }
                            } catch (Exception ignored) {}

                            try {
                                com.cosmate.entity.Notification cn = com.cosmate.entity.Notification.builder()
                                        .user(com.cosmate.entity.User.builder().id(order.getCosplayerId()).build())
                                        .type("ORDER_STATUS")
                                        .header("Đơn hàng tự động hoàn do quá hạn")
                                        .content("Đơn hàng #" + order.getId() + " đã quá hạn nên đã tự động hoàn thành. Bạn đã mất hết tiền cọc của đơn hàng này.")
                                        .sendAt(java.time.LocalDateTime.now())
                                        .isRead(false)
                                        .build();
                                notificationService.create(cn);
                            } catch (Exception ignored) {}

                            // Ban the cosplayer account according to platform rules (automatic ban on day 8)
                            try {
                                com.cosmate.entity.User cosUser = userRepository.findById(order.getCosplayerId()).orElse(null);
                                if (cosUser != null) {
                                    cosUser.setStatus("BANNED");
                                    userRepository.save(cosUser);
                                    try {
                                        com.cosmate.entity.Notification banNotif = com.cosmate.entity.Notification.builder()
                                                .user(com.cosmate.entity.User.builder().id(cosUser.getId()).build())
                                                .type("ACCOUNT_ACTION")
                                                .header("Tài khoản bị khóa do vi phạm")
                                                .content("Tài khoản của bạn đã bị khóa do vi phạm chính sách sau khi đơn hàng #" + order.getId() + " quá hạn và tự động hoàn. Liên hệ hỗ trợ để khiếu nại.")
                                                .sendAt(java.time.LocalDateTime.now())
                                                .isRead(false)
                                                .build();
                                        notificationService.create(banNotif);
                                    } catch (Exception ignored) {}
                                }
                            } catch (Exception ignored) {}

                        } catch (Exception ex) {
                            log.error("Error auto-completing overdue order {}: {}", order.getId(), ex.getMessage(), ex);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error processing candidate order {}: {}", order == null ? null : order.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception ex) {
            log.error("Error in ServiceOrderScheduler.checkOverdueRentOrders: {}", ex.getMessage(), ex);
        }
    }
}

