package com.cosmate.dto.request;

import com.cosmate.validator.ValidPhoneNumber;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @Size(max = 255)
    private String fullName;

    @ValidPhoneNumber
    private String phone;
}
