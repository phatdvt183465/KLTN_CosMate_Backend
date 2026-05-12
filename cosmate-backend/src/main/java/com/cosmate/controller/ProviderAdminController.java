package com.cosmate.controller;

import com.cosmate.base.crud.BaseCrudDataIoController;
import com.cosmate.base.crud.BaseCrudRepository;
import com.cosmate.base.crud.CrudService;
import com.cosmate.dto.filter.ProviderFilter;
import com.cosmate.dto.response.ProviderResponse;
import com.cosmate.entity.Provider;
import com.cosmate.service.ProviderAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/providers")
@RequiredArgsConstructor
public class ProviderAdminController extends BaseCrudDataIoController<Provider, Integer, ProviderResponse, ProviderFilter> {

    private final ProviderAdminService providerAdminService;

    @Override
    protected CrudService<Integer, ProviderResponse, ProviderFilter> getService() {
        return providerAdminService;
    }

    @Override
    protected BaseCrudRepository<Provider, Integer> getRepository() {
        return providerAdminService.getRepository();
    }

    @Override
    protected Class<Provider> getEntityClass() {
        return Provider.class;
    }
}
