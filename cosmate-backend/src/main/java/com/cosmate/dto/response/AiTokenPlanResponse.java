package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AiTokenPlanResponse {
    private Integer id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer numberOfToken;
    private Boolean isActive;
}

