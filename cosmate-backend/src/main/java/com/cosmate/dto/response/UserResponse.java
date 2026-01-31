package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponse {
    private Integer id;
    private String username;
    private String email;
    private String fullName;
    private String avatarUrl;
    private String phone;
    private String status;
}
