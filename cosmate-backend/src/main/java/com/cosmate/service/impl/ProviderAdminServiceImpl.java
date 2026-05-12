package com.cosmate.service.impl;

import com.cosmate.base.crud.BaseCrudMapper;
import com.cosmate.base.crud.BaseCrudRepository;
import com.cosmate.base.crud.CrudServiceImpl;
import com.cosmate.dto.filter.ProviderFilter;
import com.cosmate.dto.response.ProviderResponse;
import com.cosmate.entity.Provider;
import com.cosmate.mapper.ProviderAdminMapper;
import com.cosmate.repository.ProviderRepository;
import com.cosmate.service.ProviderAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProviderAdminServiceImpl extends CrudServiceImpl<Provider, Integer, ProviderResponse, ProviderFilter> implements ProviderAdminService {

    private final ProviderRepository providerRepository;
    private final ProviderAdminMapper providerAdminMapper;

    @Override
    public BaseCrudRepository<Provider, Integer> getRepository() {
        return providerRepository;
    }

    @Override
    protected BaseCrudMapper<Provider, ProviderResponse> getMapper() {
        return providerAdminMapper;
    }

    @Override
    protected String[] searchableFields() {
        return new String[]{"shopName", "bio", "bankName", "bankAccountNumber"};
    }

    @Override
    public Page<ProviderResponse> getAll(Pageable pageable, String search, ProviderFilter filter) {
        return super.getAllEntity(pageable, search, filter);
    }

    @Override
    public ProviderResponse getById(Integer id) {
        return super.getByIdEntity(id);
    }

    @Override
    public ProviderResponse create(ProviderResponse request) {
        return super.createEntity(request);
    }

    @Override
    public ProviderResponse update(Integer id, ProviderResponse request) {
        return super.updateEntity(id, request);
    }

    @Override
    public void delete(Integer id) {
        super.deleteEntity(id);
    }
}
