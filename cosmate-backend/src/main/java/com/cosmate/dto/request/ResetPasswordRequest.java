package com.cosmate.dto.request;

import com.cosmate.validator.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank
    private String token;

    @NotBlank
    @Size(min = 6, max = 128)
    @ValidPassword
    private String newPassword;
}

