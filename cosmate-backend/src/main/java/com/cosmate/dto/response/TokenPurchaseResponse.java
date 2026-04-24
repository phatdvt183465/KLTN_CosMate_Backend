package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TokenPurchaseResponse {
    private Integer id;
    private Integer userId;
    private Integer subscriptionId;
    private Integer transactionId;
    private BigDecimal priceAtPurchase;
    private Integer tokensAdded;
    private LocalDateTime purchaseDate;
    private String status;
}

