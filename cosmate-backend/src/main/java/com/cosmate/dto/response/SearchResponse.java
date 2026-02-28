package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class SearchResponse {
    private Integer costumeId;
    private String costumeName;
    private String imageUrl;
    private BigDecimal price;
    private double similarityScore;
}