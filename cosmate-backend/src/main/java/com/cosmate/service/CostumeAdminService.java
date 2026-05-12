package com.cosmate.service;

import com.cosmate.base.crud.CrudService;
import com.cosmate.base.crud.BaseCrudRepository;
import com.cosmate.dto.filter.CostumeFilter;
import com.cosmate.dto.response.CostumeAdminResponse;
import com.cosmate.entity.Costume;

public interface CostumeAdminService extends CrudService<Integer, CostumeAdminResponse, CostumeFilter> {
    BaseCrudRepository<Costume, Integer> getRepository();
}
