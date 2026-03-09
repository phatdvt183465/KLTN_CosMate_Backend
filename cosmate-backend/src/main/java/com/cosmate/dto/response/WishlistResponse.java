package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WishlistResponse {
    private Integer id;
    private Integer userId;
    private Integer costumeId;
    private LocalDateTime createdAt;

    // expanded: include costume details
    private com.cosmate.dto.response.CostumeResponse costume;
}
