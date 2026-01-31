package com.cosmate.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AddressRequest {
    @Size(max = 255)
    private String name;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String district;

    @Size(max = 255)
    private String address;

    @Size(max = 20)
    @Pattern(regexp = "^(?:\\+84|0)[0-9]{9,10}$", message = "INVALID_PHONE")
    private String phone;
}
