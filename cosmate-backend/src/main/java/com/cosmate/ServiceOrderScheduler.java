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
    private final com.cosmate.service.NotificationService notificationService;

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
}

