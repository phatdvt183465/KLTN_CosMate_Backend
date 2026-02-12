package com.cosmate.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {
    // Single costume per order
    private Integer costumeId;
    private Integer rentDay;
    private String rentStart; // ISO date-time string, will be parsed
    private String paymentMethod; // VNPay, MOMO, WALLET
    private String returnUrl; // optional: redirect url for VNPay/MOMO callback
    // id of the selected address of the cosplayer (Users_Addresses.id)
    private Integer cosplayerAddressId;

    // For the single costume: list of accessory ids selected (may be empty or null)
    private List<Integer> selectedAccessoryIds;
    // For the single costume: the chosen rental option id (mandatory)
    private Integer selectedRentalOptionId;
}
