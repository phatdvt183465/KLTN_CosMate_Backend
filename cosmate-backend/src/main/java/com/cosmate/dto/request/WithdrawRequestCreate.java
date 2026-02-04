package com.cosmate.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WithdrawRequestCreate {
    private BigDecimal amount;
    private String bankAccountNumber;
    private String bankName;
}
