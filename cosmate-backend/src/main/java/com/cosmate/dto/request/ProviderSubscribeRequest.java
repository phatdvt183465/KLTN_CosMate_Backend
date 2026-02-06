package com.cosmate.dto.request;

import lombok.Data;

@Data
public class ProviderSubscribeRequest {
    private Integer subscriptionPlanId;
    // optional return url for VNPay callback
    private String returnUrl;
    // optional payment method: if null -> default to VNPAY for backward compatibility
    private PaymentMethod paymentMethod;
}
