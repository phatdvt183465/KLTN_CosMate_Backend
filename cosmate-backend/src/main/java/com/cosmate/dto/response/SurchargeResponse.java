package com.cosmate.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SurchargeResponse {
    private Integer id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer costumeId; // Chỉ trả về ID, không trả về object Costume
}