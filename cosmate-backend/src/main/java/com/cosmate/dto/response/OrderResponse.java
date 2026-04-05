package com.cosmate.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderResponse {
    private Integer id;
    private Integer cosplayerId;
    private Integer providerId;
    private String orderType;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal totalDepositAmount;
    private LocalDateTime createdAt;
    private LocalDateTime rentDate;

    // When payment requires external redirect, include paymentUrl
    private String paymentUrl;
}
