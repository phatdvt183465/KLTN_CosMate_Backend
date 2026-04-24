package com.cosmate.dto.response;

import com.cosmate.base.crud.dto.CrudDto;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponse implements CrudDto<Integer> {
    private Integer id;
    private String username;
    private String email;
    private String fullName;
    private String avatarUrl;
    private String role;
    private String phone;
    private String status;
    private Integer numberOfToken;
}