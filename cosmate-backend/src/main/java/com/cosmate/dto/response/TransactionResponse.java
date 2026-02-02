package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TransactionResponse {
    private Integer id;
    private BigDecimal amount;
    private String type;
    private String status;
    private LocalDateTime createdAt;
}
