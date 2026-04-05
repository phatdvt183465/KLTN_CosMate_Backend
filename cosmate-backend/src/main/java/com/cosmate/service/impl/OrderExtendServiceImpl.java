package com.cosmate.service.impl;

import com.cosmate.dto.request.OrderExtendRequest;
import com.cosmate.dto.response.OrderExtendResponse;
import com.cosmate.entity.Order;
import com.cosmate.entity.OrderDetail;
import com.cosmate.entity.OrderDetailExtend;
import com.cosmate.entity.User;
import com.cosmate.repository.OrderDetailExtendRepository;
import com.cosmate.repository.TransactionRepository;
import com.cosmate.service.VnPayService;
import com.cosmate.service.MomoService;
import com.cosmate.repository.OrderDetailRepository;
import com.cosmate.repository.OrderRepository;
import com.cosmate.service.OrderExtendService;
import com.cosmate.service.WalletService;
import com.cosmate.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrderExtendServiceImpl implements OrderExtendService {
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final OrderDetailExtendRepository orderDetailExtendRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final VnPayService vnPayService;
    private final MomoService momoService;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional
    public OrderExtendResponse requestExtend(Integer userId, Integer orderId, Integer orderDetailId, OrderExtendRequest req) throws Exception {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (order.getCosplayerId() == null || !order.getCosplayerId().equals(userId)) throw new IllegalArgumentException("No permission to extend this order");
        // Cannot request extend once order has reached SHIPPING_BACK
        if ("SHIPPING_BACK".equals(order.getStatus()) || "COMPLETED".equals(order.getStatus()) || "CANCELLED".equals(order.getStatus())) {
            throw new IllegalArgumentException("Cannot request extend after order progressed to SHIPPING_BACK or beyond");
        }

        OrderDetail detail = orderDetailRepository.findById(orderDetailId).orElseThrow(() -> new IllegalArgumentException("Order detail not found"));
        if (detail.getOrderId() == null || !detail.getOrderId().equals(orderId)) throw new IllegalArgumentException("Order detail does not belong to order");

        if (req.getExtendDays() == null || req.getExtendDays() <= 0) throw new IllegalArgumentException("extendDays must be >= 1");

        // Use rentEnd as the canonical return date for extension requests
        LocalDateTime oldReturn = detail.getRentEnd();
        if (oldReturn == null) {
            throw new IllegalArgumentException("Order detail has no rent_end to extend");
        }
        LocalDateTime newReturn = oldReturn.plusDays(req.getExtendDays());

        BigDecimal extendPrice = calculateExtendPrice(detail, req.getExtendDays());

        OrderDetailExtend ext = OrderDetailExtend.builder()
                .orderDetailId(orderDetailId)
                .oldReturnDate(oldReturn)
                .newReturnDate(newReturn)
                .extendDays(req.getExtendDays())
                .extendPrice(extendPrice)
                .paymentStatus("UNPAID")
                .createdAt(LocalDateTime.now())
                .build();
        ext = orderDetailExtendRepository.save(ext);

        // notify user
        try {
            com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                    .user(User.builder().id(userId).build())
                    .type("EXTEND_REQUEST")
                    .header("Yêu cầu gia hạn")
                    .content("Yêu cầu gia hạn đơn chi tiết #" + detail.getId() + " thêm " + req.getExtendDays() + " ngày. Giá: " + extendPrice)
                    .sendAt(LocalDateTime.now())
                    .isRead(false)
                    .build();
            notificationService.create(n);
        } catch (Exception ignored) {}

        // If user requested immediate payment, handle according to paymentMethod
        if (Boolean.TRUE.equals(req.getPayNow())) {
            String pm = req.getPaymentMethod();
            if (pm == null) pm = "WALLET";
            pm = pm.trim();
            if ("WALLET".equalsIgnoreCase(pm)) {
                payExtend(userId, orderId, ext.getId(), "WALLET", null);
            } else if ("VNPay".equalsIgnoreCase(pm) || "VNPAY".equalsIgnoreCase(pm)) {
                // create pending transaction and return payment url
                com.cosmate.entity.User u = com.cosmate.entity.User.builder().id(userId).build();
                com.cosmate.entity.Wallet wallet = walletService.createForUser(u);
                com.cosmate.entity.Transaction pending = com.cosmate.entity.Transaction.builder()
                        .wallet(wallet)
                        .amount(ext.getExtendPrice())
                        .type("EXTEND#" + ext.getId())
                        .paymentMethod("VNPAY")
                        .status("PENDING")
                        .createdAt(LocalDateTime.now())
                        .build();
                pending = transactionRepository.save(pending);
                String returnUrl = req.getReturnUrl();
                if (returnUrl == null || returnUrl.isEmpty()) returnUrl = "/api/payments/vnpay-return";
                String paymentUrl = vnPayService.createPaymentUrlForTransaction(userId, ext.getExtendPrice(), returnUrl, pending.getId());
                OrderExtendResponse dto = toDto(ext);
                dto.setPaymentUrl(paymentUrl);
                return dto;
            } else if ("MOMO".equalsIgnoreCase(pm)) {
                com.cosmate.entity.User u = com.cosmate.entity.User.builder().id(userId).build();
                com.cosmate.entity.Wallet wallet = walletService.createForUser(u);
                com.cosmate.entity.Transaction pending = com.cosmate.entity.Transaction.builder()
                        .wallet(wallet)
                        .amount(ext.getExtendPrice())
                        .type("EXTEND#" + ext.getId())
                        .paymentMethod("MOMO")
                        .status("PENDING")
                        .createdAt(LocalDateTime.now())
                        .build();
                pending = transactionRepository.save(pending);
                String returnUrl = req.getReturnUrl();
                if (returnUrl == null || returnUrl.isEmpty()) returnUrl = "/api/payments/momo-return";
                String paymentUrl = momoService.createPaymentUrlForTransaction(userId, ext.getExtendPrice(), returnUrl, pending.getId());
                OrderExtendResponse dto = toDto(ext);
                dto.setPaymentUrl(paymentUrl);
                return dto;
            } else {
                // unknown payment method - default to wallet
                payExtend(userId, orderId, ext.getId(), "WALLET", null);
            }
        }

        return toDto(ext);
    }

    @Override
    @Transactional
    public OrderExtendResponse payExtend(Integer userId, Integer orderId, Integer extendId, String paymentMethod, String returnUrl) throws Exception {
        OrderDetailExtend ext = orderDetailExtendRepository.findById(extendId).orElseThrow(() -> new IllegalArgumentException("Extend request not found"));
        if ("PAID".equals(ext.getPaymentStatus())) return toDto(ext); // idempotent

        Order order = orderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (order.getCosplayerId() == null || !order.getCosplayerId().equals(userId)) throw new IllegalArgumentException("No permission to pay this extend");
        String pm = paymentMethod == null ? "WALLET" : paymentMethod;
        if ("WALLET".equalsIgnoreCase(pm)) {
            com.cosmate.entity.User u = com.cosmate.entity.User.builder().id(userId).build();
            com.cosmate.entity.Wallet wallet = walletService.createForUser(u);
            walletService.debit(wallet, ext.getExtendPrice(), "Extend payment", "EXTEND:" + ext.getId(), "WALLET", order);
            ext.setPaymentStatus("PAID");
            ext = orderDetailExtendRepository.save(ext);

            try {
                com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                        .user(User.builder().id(userId).build())
                        .type("EXTEND_PAID")
                        .header("Thanh toán gia hạn thành công")
                        .content("Đã thanh toán gia hạn cho đơn chi tiết #" + ext.getOrderDetailId() + ". Số tiền: " + ext.getExtendPrice())
                        .sendAt(LocalDateTime.now())
                        .isRead(false)
                        .build();
                notificationService.create(n);
            } catch (Exception ignored) {}
            return toDto(ext);
        } else if ("VNPay".equalsIgnoreCase(pm) || "VNPAY".equalsIgnoreCase(pm)) {
            // create pending transaction
            com.cosmate.entity.User u = com.cosmate.entity.User.builder().id(userId).build();
            com.cosmate.entity.Wallet wallet = walletService.createForUser(u);
            com.cosmate.entity.Transaction pending = com.cosmate.entity.Transaction.builder()
                    .wallet(wallet)
                    .amount(ext.getExtendPrice())
                    .type("EXTEND#" + ext.getId())
                    .paymentMethod("VNPAY")
                    .status("PENDING")
                    .createdAt(LocalDateTime.now())
                    .build();
            pending = transactionRepository.save(pending);
            String r = returnUrl == null || returnUrl.isEmpty() ? "/api/payments/vnpay-return" : returnUrl;
            String paymentUrl = vnPayService.createPaymentUrlForTransaction(userId, ext.getExtendPrice(), r, pending.getId());
            OrderExtendResponse dto = toDto(ext);
            dto.setPaymentUrl(paymentUrl);
            return dto;
        } else if ("MOMO".equalsIgnoreCase(pm)) {
            com.cosmate.entity.User u = com.cosmate.entity.User.builder().id(userId).build();
            com.cosmate.entity.Wallet wallet = walletService.createForUser(u);
            com.cosmate.entity.Transaction pending = com.cosmate.entity.Transaction.builder()
                    .wallet(wallet)
                    .amount(ext.getExtendPrice())
                    .type("EXTEND#" + ext.getId())
                    .paymentMethod("MOMO")
                    .status("PENDING")
                    .createdAt(LocalDateTime.now())
                    .build();
            pending = transactionRepository.save(pending);
            String r = returnUrl == null || returnUrl.isEmpty() ? "/api/payments/momo-return" : returnUrl;
            String paymentUrl = momoService.createPaymentUrlForTransaction(userId, ext.getExtendPrice(), r, pending.getId());
            OrderExtendResponse dto = toDto(ext);
            dto.setPaymentUrl(paymentUrl);
            return dto;
        }
        throw new IllegalArgumentException("Unsupported payment method: " + paymentMethod);
    }

    @Override
    @Transactional
    public OrderExtendResponse cancelExtend(Integer userId, Integer orderId, Integer extendId) throws Exception {
        OrderDetailExtend ext = orderDetailExtendRepository.findById(extendId).orElseThrow(() -> new IllegalArgumentException("Extend request not found"));
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (order.getCosplayerId() == null || !order.getCosplayerId().equals(userId)) throw new IllegalArgumentException("No permission to cancel this extend");

        // cannot cancel after order moved to EXTENDING
        if ("EXTENDING".equals(order.getStatus()) || "SHIPPING_BACK".equals(order.getStatus()) || "COMPLETED".equals(order.getStatus())) {
            throw new IllegalArgumentException("Cannot cancel extend after order progressed to EXTENDING/SHIPPING_BACK");
        }

        if ("PAID".equals(ext.getPaymentStatus())) {
            // refund to user's wallet
            com.cosmate.entity.User u = com.cosmate.entity.User.builder().id(userId).build();
            com.cosmate.entity.Wallet wallet = walletService.createForUser(u);
            walletService.credit(wallet, ext.getExtendPrice(), "Refund extend cancellation", "EXTEND_REFUND:" + ext.getId(), null, order);
            ext.setPaymentStatus("CANCELLED");
        } else {
            ext.setPaymentStatus("CANCELLED");
        }
        ext = orderDetailExtendRepository.save(ext);

        try {
            com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                    .user(User.builder().id(userId).build())
                    .type("EXTEND_CANCELLED")
                    .header("Đã hủy yêu cầu gia hạn")
                    .content("Yêu cầu gia hạn cho đơn chi tiết #" + ext.getOrderDetailId() + " đã bị hủy.")
                    .sendAt(LocalDateTime.now())
                    .isRead(false)
                    .build();
            notificationService.create(n);
        } catch (Exception ignored) {}

        return toDto(ext);
    }

    private OrderExtendResponse toDto(OrderDetailExtend e) {
        OrderExtendResponse r = new OrderExtendResponse();
        r.setId(e.getId());
        r.setOrderDetailId(e.getOrderDetailId());
        r.setOldReturnDate(e.getOldReturnDate());
        r.setNewReturnDate(e.getNewReturnDate());
        r.setExtendDays(e.getExtendDays());
        r.setExtendPrice(e.getExtendPrice());
        r.setPaymentStatus(e.getPaymentStatus());
        r.setCreatedAt(e.getCreatedAt());
        return r;
    }

    private BigDecimal calculateExtendPrice(OrderDetail detail, Integer extendDays) {
        if (detail.getRentAmount() == null || detail.getRentDay() == null || detail.getRentDay() <= 0) return BigDecimal.ZERO;
        BigDecimal rentAmount = detail.getRentAmount();
        int originalDays = detail.getRentDay();
        // rentDiscount is a percentage (e.g., 50 means subsequent days charged at 50% of pricePerDay)
        int rentDiscountInt = detail.getRentDiscount() == null ? 100 : detail.getRentDiscount();
        BigDecimal rentDiscountPct = new BigDecimal(rentDiscountInt);

        // Reverse the order's rent calculation to obtain the original pricePerDay.
        // For original order when days > 1:
        // rentAmount = pricePerDay + subsequentRate * (originalDays - 1)
        // where subsequentRate = pricePerDay * (rentDiscountPct / 100)
        // => rentAmount = pricePerDay * (1 + (originalDays - 1) * rentDiscountPct / 100)
        BigDecimal pricePerDay;
        if (originalDays <= 1) {
            pricePerDay = rentAmount;
        } else {
            BigDecimal multiplier = BigDecimal.ONE.add(new BigDecimal(originalDays - 1).multiply(rentDiscountPct).divide(new BigDecimal(100), 8, RoundingMode.HALF_UP));
            // protect against division by zero
            if (multiplier.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
            pricePerDay = rentAmount.divide(multiplier, 8, RoundingMode.HALF_UP);
        }

        // subsequent-day rate used for extension (same as in original order)
        BigDecimal subsequentRate = pricePerDay.multiply(rentDiscountPct).divide(new BigDecimal(100), 8, RoundingMode.HALF_UP);
        BigDecimal total = subsequentRate.multiply(new BigDecimal(extendDays));
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}


