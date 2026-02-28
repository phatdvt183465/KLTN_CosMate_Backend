package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ServiceResponse {
    private Integer id;
    private String serviceType;
    private String description;
    private Integer slotDurationHours;
    private BigDecimal pricePerSlot;
    private BigDecimal equipmentDepreciationCost;
    private String status;
    private Integer providerId;
    private List<String> areas;
    private List<String> imageUrls;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
}