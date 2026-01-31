package com.cosmate.dto.request;

import lombok.Data;

@Data
public class GoogleRegisterRequest {
    private String email;
    private String fullName;
    private String avatarUrl;
}
