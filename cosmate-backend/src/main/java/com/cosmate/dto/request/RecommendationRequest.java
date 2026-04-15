package com.cosmate.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RecommendationRequest {
    @NotBlank(message = "ARCHETYPE_ID_INVALID")
    @Size(max = 50)
    private String archetypeId;    // VD: "ARCH_01" (Kết quả từ Stage 1)

    @NotBlank(message = "SUBTYPE_ID_INVALID")
    @Size(max = 50)
    private String subTypeId;      // VD: "01_A" (Kết quả từ Stage 2)

    @Size(max = 50)
    private String budgetMetadata; // VD: "mid_budget"
}
