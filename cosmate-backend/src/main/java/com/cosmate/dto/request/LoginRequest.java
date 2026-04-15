package com.cosmate.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "USERNAME_OR_EMAIL_INVALID")
    @Size(min = 3, max = 255)
    private String usernameOrEmail;

    @NotBlank(message = "PASSWORD_INVALID")
    @Size(min = 6, max = 128)
    private String password;
}
