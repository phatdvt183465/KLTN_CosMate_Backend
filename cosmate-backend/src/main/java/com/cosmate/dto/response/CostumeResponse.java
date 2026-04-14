package com.cosmate.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostumeResponse {
    private Integer id;
    private String name;
    private String description;
    private String size;
    private String rentPurpose;
    private Integer numberOfItems;
    private BigDecimal pricePerDay;
    private BigDecimal depositAmount;
    private Integer rentDiscount;
    private String status;
    private Integer providerId;
    private Integer completedRentCount;
    private List<String> imageUrls;
    private List<SurchargeResponse> surcharges;
    private List<AccessoryResponse> accessories;
    private List<RentalOptionResponse> rentalOptions;

    @Data
    @Builder
    public static class SurchargeResponse {
        private Integer id;
        private String name;
        private String description;
        private BigDecimal price;
    }

    @Data
    @Builder
    public static class AccessoryResponse {
        private Integer id;
        private String name;
        private String description;
        private BigDecimal price;
        private Boolean isRequired;
    }

    @Data
    @Builder
    public static class RentalOptionResponse {
        private Integer id;
        private String name;
        private BigDecimal price;
        private String description;
    }
}