package com.cosmate.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderExtendResponse {
    private Integer id;
    private Integer orderDetailId;
    private LocalDateTime oldReturnDate;
    private LocalDateTime newReturnDate;
    private Integer extendDays;
    private BigDecimal extendPrice;
    private String paymentStatus;
    private LocalDateTime createdAt;
    private String paymentUrl;
}

