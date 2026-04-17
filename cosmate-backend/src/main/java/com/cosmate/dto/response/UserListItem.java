package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserListItem {
    private Integer id;
    private String username;
    private String email;
    private String fullName;
    private String avatarUrl;
    private String phone;
    private String status;
    private String role;
    private LocalDateTime createdAt;
}
