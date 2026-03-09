package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProviderResponse {
    private Integer id;
    private Integer userId;
    private String shopName;
    private Integer shopAddressId;
    private String avatarUrl;
    private String coverImageUrl;
    private String bio;
    private String bankAccountNumber;
    private String bankName;
    private Boolean verified;

    private Integer completedOrders;
    private Integer totalRating;
    private Integer totalReviews;
}
