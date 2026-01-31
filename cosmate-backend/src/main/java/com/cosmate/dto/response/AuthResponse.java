package com.cosmate.dto.response;

import lombok.Data;

@Data
public class AuthResponse {
    private String token;
    private String tokenType = "Bearer";
}
