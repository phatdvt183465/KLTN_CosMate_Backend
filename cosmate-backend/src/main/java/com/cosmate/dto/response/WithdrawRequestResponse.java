package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class WithdrawRequestResponse {
    private Integer id;
    private Integer userId;
    private Integer walletId;
    private BigDecimal amount;
    private String bankAccountNumber;
    private String bankName;
    private String status;
    private LocalDateTime requestedAt;
}
