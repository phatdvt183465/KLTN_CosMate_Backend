package com.cosmate.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.cosmate.base.crud.dto.CrudDto;

@Data
public class OrderResponse implements CrudDto<Integer> {
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
