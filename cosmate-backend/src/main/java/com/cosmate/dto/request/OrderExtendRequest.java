package com.cosmate.dto.request;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
public class OrderExtendRequest {
    @NotNull
    @Min(1)
    private Integer extendDays;

    // payment method: WALLET, VNPay, MOMO (case-insensitive). If null default to WALLET when payNow=true
    private String paymentMethod;

    // optional return url for external payments (VNPay/MOMO). If absent a default is used
    private String returnUrl;

    private Boolean payNow = false;
}


