package com.cosmate.dto.response;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderAdminResponse extends OrderResponse {
    private String userName;
    private String cosplayerName;
    private String providerName;
    private String code;
}
