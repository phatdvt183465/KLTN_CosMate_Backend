package com.cosmate.service;

import com.cosmate.base.crud.CrudService;
import com.cosmate.base.crud.BaseCrudRepository;
import com.cosmate.dto.filter.ProviderFilter;
import com.cosmate.dto.response.ProviderResponse;
import com.cosmate.entity.Provider;

public interface ProviderAdminService extends CrudService<Integer, ProviderResponse, ProviderFilter> {
    BaseCrudRepository<Provider, Integer> getRepository();
}
