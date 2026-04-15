package com.cosmate.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordResetRequest {
    @NotBlank
    @Size(min = 3, max = 255)
    private String identifier; // email or username
}

