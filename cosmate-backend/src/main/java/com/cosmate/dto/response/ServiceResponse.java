package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ServiceResponse {
    private Integer id;
    private String name;
    private String description;
    private BigDecimal price;
    private String status;
    private Integer providerId;
    private List<String> areas;
    private List<String> imageUrls;
}