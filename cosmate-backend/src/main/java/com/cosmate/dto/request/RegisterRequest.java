package com.cosmate.dto.request;

import com.cosmate.validator.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "USERNAME_INVALID")
    @Size(min = 3, max = 100)
    @Pattern(regexp = RequestValidation.USERNAME_REGEX, message = "USERNAME_INVALID")
    private String username;

    @NotBlank(message = "EMAIL_INVALID")
    @Email(message = "EMAIL_INVALID")
    private String email;

    // Make password optional here; service will validate when needed
    @Size(min = 6, max = 128)
    @ValidPassword
    private String password;

    // optional phone; basic Vietnam phone pattern: starts with +84 or 0 then 9-10 digits
    @Pattern(regexp = RequestValidation.PHONE_REGEX, message = "INVALID_PHONE")
    private String phone;

    // optional
    private String fullName;

    // optional role requested by the client (ADMIN, COSPLAYER, PROVIDER_RENTAL, PROVIDER_PHOTOGRAPH, PROVIDER_EVENT_STAFF, STAFF)
    private String role;

    // avatarUrl removed: uploads are handled via multipart file and passed separately to service
}
