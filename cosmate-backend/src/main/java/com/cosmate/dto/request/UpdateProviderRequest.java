package com.cosmate.dto.request;

import lombok.Data;

@Data
public class UpdateProviderRequest {
    private String shopName;
    private Integer shopAddressId;
    // avatarUrl removed as requested — avatar is managed on User and mirrored to Provider when role is PROVIDER
    private String bio;
    private String bankAccountNumber;
    private String bankName;
}
