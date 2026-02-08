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
    private Long id;
    private String name;
    private String description;
    private String size;
    private String rentPurpose;
    private Integer numberOfItems;
    private BigDecimal pricePerDay;   // Trả về đúng tên
    private BigDecimal depositAmount; // Trả về đúng tên
    private String status;
    private Long providerId;          // Trả về đúng tên
    private List<String> imageUrls;
}