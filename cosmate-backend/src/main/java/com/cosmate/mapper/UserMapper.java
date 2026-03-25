package com.cosmate.mapper;

import com.cosmate.entity.User;
import com.cosmate.dto.response.UserResponse;
import com.cosmate.base.crud.BaseCrudMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface UserMapper extends BaseCrudMapper<User, UserResponse> {

    // Chiều 1: Từ Database (Entity) ra API (DTO) -> Lấy roleName
    @Override
    @Mapping(source = "role.roleName", target = "role")
    UserResponse toResponse(User entity);

    // Chiều 2: Từ API (DTO) dội ngược vào Database (Entity) -> Bỏ qua trường role
    @Override
    @Mapping(target = "role", ignore = true)
    User toEntity(UserResponse dto);

    // Chiều 3: Update Entity từ DTO -> Bỏ qua trường role
    @Override
    @Mapping(target = "role", ignore = true)
    void update(@MappingTarget User entity, UserResponse dto);

}