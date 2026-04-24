package com.cosmate.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AiTokenPlanRequest {
    private String name;
    private String description;
    private BigDecimal price;
    private Integer numberOfToken;
}

