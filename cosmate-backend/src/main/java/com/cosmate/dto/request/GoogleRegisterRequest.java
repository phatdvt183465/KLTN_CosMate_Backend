package com.cosmate.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GoogleRegisterRequest {
    @NotBlank(message = "EMAIL_INVALID")
    @Email(message = "EMAIL_INVALID")
    private String email;

    @Size(max = 255)
    private String fullName;

    @Size(max = 2048)
    private String avatarUrl;
}
