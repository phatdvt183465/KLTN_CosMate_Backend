package com.cosmate.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderSubscriptionItemResponse {
    private Integer id;
    private String name;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String status;
    private BigDecimal price;
    private Integer monthlyToken;
    private Long daysRemaining;
}

