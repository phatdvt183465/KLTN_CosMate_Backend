package com.cosmate.service.impl;

import com.cosmate.base.crud.BaseCrudMapper;
import com.cosmate.base.crud.BaseCrudRepository;
import com.cosmate.base.crud.CrudServiceImpl;
import com.cosmate.dto.filter.UserFilter;
import com.cosmate.dto.response.UserResponse;
import com.cosmate.entity.User;
import com.cosmate.mapper.UserMapper;
import com.cosmate.repository.UserRepository;
import com.cosmate.service.UserAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserAdminServiceImpl extends CrudServiceImpl<User, Integer, UserResponse, UserFilter> implements UserAdminService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    protected BaseCrudRepository<User, Integer> getRepository() { return userRepository; }

    @Override
    protected BaseCrudMapper<User, UserResponse> getMapper() { return userMapper; }

    @Override
    protected String[] searchableFields() { return new String[]{"username", "email", "fullName", "phone"}; }

    @Override
    public Page<UserResponse> getAll(Pageable pageable, String search, UserFilter filter) {
        return super.getAllEntity(pageable, search, filter);
    }

    @Override
    public UserResponse getById(Integer id) {
        return super.getByIdEntity(id);
    }

    @Override
    public UserResponse create(UserResponse request) {
        return super.createEntity(request);
    }

    @Override
    public UserResponse update(Integer id, UserResponse request) {
        return super.updateEntity(id, request);
    }

    @Override
    public void delete(Integer id) {
        super.deleteEntity(id);
    }
}