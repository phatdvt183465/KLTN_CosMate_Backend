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
    private String status;
    private Integer providerId;
    private List<String> imageUrls;
    private List<SurchargeResponse> surcharges; // Thêm list phụ phí ở đây nè anh [cite: 2]

    @Data
    @Builder
    public static class SurchargeResponse {
        private String name;
        private String description;
        private BigDecimal price;
    }
}