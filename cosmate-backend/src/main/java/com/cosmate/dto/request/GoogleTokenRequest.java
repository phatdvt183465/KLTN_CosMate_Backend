package com.cosmate.dto.request;

import lombok.Data;

@Data
public class GoogleTokenRequest {
    // id_token (JWT) obtained from Google's OAuth2 client on frontend
    private String idToken;
}
