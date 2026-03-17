package com.cosmate.controller;

import com.cosmate.base.crud.BaseCrudDataIoController;
import com.cosmate.base.crud.BaseCrudRepository;
import com.cosmate.base.crud.CrudService;
import com.cosmate.dto.filter.UserFilter;
import com.cosmate.dto.response.UserResponse;
import com.cosmate.entity.User;
import com.cosmate.repository.UserRepository;
import com.cosmate.service.UserAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserAdminController extends BaseCrudDataIoController<User, Integer, UserResponse, UserFilter> {

    private final UserAdminService userAdminService;
    private final UserRepository userRepository;

    @Override
    protected CrudService<Integer, UserResponse, UserFilter> getService() { return userAdminService; }

    @Override
    protected BaseCrudRepository<User, Integer> getRepository() { return userRepository; }

    @Override
    protected Class<User> getEntityClass() { return User.class; }
}