package com.cosmate.dto.request;

import com.cosmate.validation.RequestValidation;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {
    // Single costume per order
    @NotNull(message = "COSTUME_ID_INVALID")
    @Min(value = 1, message = "COSTUME_ID_INVALID")
    private Integer costumeId;

    @NotNull(message = "RENT_DAY_INVALID")
    @Min(value = 1, message = "RENT_DAY_INVALID")
    private Integer rentDay;

    @NotBlank(message = "RENT_START_INVALID")
    private String rentStart; // ISO date-time string, will be parsed

    @NotBlank(message = "PAYMENT_METHOD_INVALID")
    @Pattern(regexp = RequestValidation.PAYMENT_METHOD_REGEX, message = "PAYMENT_METHOD_INVALID")
    private String paymentMethod; // VNPay, MOMO, WALLET

    @Size(max = 2048)
    private String returnUrl; // optional: redirect url for VNPay/MOMO callback

    // id of the selected address of the cosplayer (Users_Addresses.id)
    @NotNull(message = "COSPLAYER_ADDRESS_ID_INVALID")
    @Min(value = 1, message = "COSPLAYER_ADDRESS_ID_INVALID")
    private Integer cosplayerAddressId;

    // For the single costume: list of accessory ids selected (may be empty or null)
    private List<@Min(value = 1, message = "ACCESSORY_ID_INVALID") Integer> selectedAccessoryIds;

    // For the single costume: the chosen rental option id (mandatory)
    @NotNull(message = "RENTAL_OPTION_ID_INVALID")
    @Min(value = 1, message = "RENTAL_OPTION_ID_INVALID")
    private Integer selectedRentalOptionId;
}
