package com.cosmate.dto.request;

import lombok.Data;

@Data
public class GoogleTokenRequest {
    // Either provide idToken (JWT) obtained on frontend or an authorization code from OAuth2 Authorization Code flow
    private String idToken;

    // Authorization code (server-side exchange) for OAuth2.0 Authorization Code flow
    private String code;

    // Redirect URI used when exchanging the authorization code (should match the one used when obtaining the code)
    private String redirectUri;
}
