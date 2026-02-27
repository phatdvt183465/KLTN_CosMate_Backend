package com.cosmate.dto.response;

import com.cosmate.entity.OrderServiceBooking;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ServiceOrderItemResponse {
    private Integer id;
    private Integer cosplayerId;
    private Integer providerId;
    private String orderType;
    private String status;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;

    // service booking details
    private List<OrderServiceBooking> bookings;
}

