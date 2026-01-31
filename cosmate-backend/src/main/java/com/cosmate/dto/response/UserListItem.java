package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
public class UserListItem {
    private Integer id;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private String status;
    private Set<String> roles;
    private LocalDateTime createdAt;
}
