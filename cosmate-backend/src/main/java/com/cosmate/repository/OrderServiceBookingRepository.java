package com.cosmate.repository;

import com.cosmate.entity.OrderServiceBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderServiceBookingRepository extends JpaRepository<OrderServiceBooking, Integer> {
    List<OrderServiceBooking> findByOrderId(Integer orderId);
}
