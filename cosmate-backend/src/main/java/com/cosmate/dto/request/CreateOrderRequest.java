package com.cosmate.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {
    private List<Integer> costumesId;
    private Integer rentDay;
    private String rentStart; // ISO date-time string, will be parsed
    private String paymentMethod; // VNPay, MOMO, WALLET
    private String returnUrl; // optional: redirect url for VNPay/MOMO callback
}
