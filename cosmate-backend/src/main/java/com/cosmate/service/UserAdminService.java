package com.cosmate.service;

import com.cosmate.base.crud.CrudService;
import com.cosmate.base.crud.BaseCrudRepository;
import com.cosmate.dto.filter.UserFilter;
import com.cosmate.dto.response.UserResponse;
import com.cosmate.entity.User;

public interface UserAdminService extends CrudService<Integer, UserResponse, UserFilter> {
	// Expose repository for controllers that extend BaseCrudDataIoController
	BaseCrudRepository<User, Integer> getRepository();
}