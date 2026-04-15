package com.cosmate.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GoogleTokenRequest {
    // id_token (JWT) obtained from Google's OAuth2 client on frontend
    @NotBlank(message = "ID_TOKEN_INVALID")
    @Size(min = 10, max = 4096)
    private String idToken;
}
