package com.cosmate.service;

import com.cosmate.base.crud.CrudService;
import com.cosmate.dto.filter.UserFilter;
import com.cosmate.dto.response.UserResponse;

public interface UserAdminService extends CrudService<Integer, UserResponse, UserFilter> {
}