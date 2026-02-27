package com.cosmate;

import com.cosmate.entity.Order;
import com.cosmate.entity.OrderServiceBooking;
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
                log.info("Auto-updated order {} to IN_SERVICE for booking date {}", orderId, today);
            }
        } catch (Exception ex) {
            log.error("Error in ServiceOrderScheduler.startServiceOnBookingDate: {}", ex.getMessage(), ex);
        }
    }
}

