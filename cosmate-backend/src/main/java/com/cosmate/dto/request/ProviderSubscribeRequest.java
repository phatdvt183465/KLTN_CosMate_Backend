package com.cosmate.dto.request;

import lombok.Data;

@Data
public class ProviderSubscribeRequest {
    private Integer subscriptionPlanId;
    // optional return url for VNPay callback
    private String returnUrl;
}
