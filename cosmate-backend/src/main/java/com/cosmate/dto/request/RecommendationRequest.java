package com.cosmate.dto.request;
import lombok.Data;

@Data
public class RecommendationRequest {
    private String archetypeId;    // VD: "ARCH_01" (Kết quả từ Stage 1)
    private String subTypeId;      // VD: "01_A" (Kết quả từ Stage 2)
    private String budgetMetadata; // VD: "mid_budget"
}