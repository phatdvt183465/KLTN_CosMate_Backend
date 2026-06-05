package com.cosmate.controller;

import com.cosmate.base.crud.BaseCrudDataIoController;
import com.cosmate.base.crud.BaseCrudRepository;
import com.cosmate.base.crud.CrudService;
import com.cosmate.dto.filter.ProviderFilter;
import com.cosmate.dto.response.ProviderResponse;
import com.cosmate.entity.Provider;
import com.cosmate.service.ProviderAdminService;
import com.cosmate.service.ProviderService;
import org.springframework.web.bind.annotation.PostMapping;
import com.cosmate.dto.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/providers")
@RequiredArgsConstructor
public class ProviderAdminController extends BaseCrudDataIoController<Provider, Integer, ProviderResponse, ProviderFilter> {

    private final ProviderAdminService providerAdminService;
    private final ProviderService providerService;

    @Override
    protected CrudService<Integer, ProviderResponse, ProviderFilter> getService() {
        return providerAdminService;
    }

    @Override
    protected BaseCrudRepository<Provider, Integer> getRepository() {
        return providerAdminService.getRepository();
    }

    @PostMapping("/recalculate-totals")
    public ApiResponse<String> recalculateAllProviderTotals() {
        providerService.recalculateAllProviderTotals();
        return ApiResponse.<String>builder().result("ok").message("Recalculation completed").build();
    }

    @Override
    protected Class<Provider> getEntityClass() {
        return Provider.class;
    }
}
