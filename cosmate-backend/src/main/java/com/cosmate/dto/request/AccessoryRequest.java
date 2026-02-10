package com.cosmate.dto.request;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class AccessoryRequest {
    private String name;
    private String description;
    private BigDecimal price;
    private Boolean isRequired;
}