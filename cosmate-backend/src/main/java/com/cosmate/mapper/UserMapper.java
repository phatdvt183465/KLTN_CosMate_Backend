package com.cosmate.mapper;

import com.cosmate.entity.User;
import com.cosmate.dto.response.UserResponse;
import com.cosmate.base.crud.BaseCrudMapper;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper extends BaseCrudMapper<User, UserResponse> {
}