package com.cosmate.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @Size(max = 255)
    private String fullName;

    @Pattern(regexp = "^(?:\\+84|0)[0-9]{9,10}$", message = "INVALID_PHONE")
    private String phone;

    private String avatarUrl;
}
