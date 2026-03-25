package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatPartnerProfileResponse {
    private Integer partnerId;
    private String fullName;
    private String avatarUrl;
}