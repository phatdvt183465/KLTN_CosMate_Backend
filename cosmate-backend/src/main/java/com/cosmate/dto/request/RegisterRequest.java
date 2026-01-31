package com.cosmate.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "USERNAME_INVALID")
    @Size(min = 3, max = 100)
    private String username;

    @NotBlank(message = "EMAIL_INVALID")
    @Email(message = "EMAIL_INVALID")
    private String email;

    // Make password optional here; service will validate when needed
    @Size(min = 6, max = 128)
    private String password;

    // optional phone; basic Vietnam phone pattern: starts with +84 or 0 then 9-10 digits
    @Pattern(regexp = "^(?:\\+84|0)[0-9]{9,10}$", message = "INVALID_PHONE")
    private String phone;

    // optional
    private String fullName;
    private String avatarUrl;

    // optional role requested by the client (ADMIN, COSPLAYER, PROVIDER, STAFF)
    private String role;
}
