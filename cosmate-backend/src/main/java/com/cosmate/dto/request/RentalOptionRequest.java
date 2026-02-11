package com.cosmate.dto.request;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class RentalOptionRequest {
    private String name;
    private BigDecimal price;
    private String description;
}