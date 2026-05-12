package com.cosmate.service.impl;

import com.cosmate.base.crud.BaseCrudMapper;
import com.cosmate.base.crud.BaseCrudRepository;
import com.cosmate.base.crud.CrudServiceImpl;
import com.cosmate.dto.filter.CostumeFilter;
import com.cosmate.dto.response.CostumeAdminResponse;
import com.cosmate.entity.Costume;
import com.cosmate.entity.Provider;
import com.cosmate.mapper.CostumeAdminMapper;
import com.cosmate.repository.CostumeRepository;
import com.cosmate.repository.ProviderRepository;
import com.cosmate.service.CostumeAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CostumeAdminServiceImpl extends CrudServiceImpl<Costume, Integer, CostumeAdminResponse, CostumeFilter> implements CostumeAdminService {

    private final CostumeRepository costumeRepository;
    private final CostumeAdminMapper costumeAdminMapper;
    private final ProviderRepository providerRepository;

    @Override
    public BaseCrudRepository<Costume, Integer> getRepository() {
        return costumeRepository;
    }

    @Override
    protected BaseCrudMapper<Costume, CostumeAdminResponse> getMapper() {
        return costumeAdminMapper;
    }

    @Override
    protected String[] searchableFields() {
        return new String[]{"name", "description"};
    }

    @Override
    public Page<CostumeAdminResponse> getAll(Pageable pageable, String search, CostumeFilter filter) {
        Page<CostumeAdminResponse> page = super.getAllEntity(pageable, search, filter);
        List<Integer> providerIds = page.getContent().stream()
                .map(CostumeAdminResponse::getProviderId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

        if (!providerIds.isEmpty()) {
            Map<Integer, String> providerNames = providerRepository.findAllById(providerIds)
                    .stream()
                    .collect(Collectors.toMap(Provider::getId, Provider::getShopName));
            page.getContent().forEach(resp -> {
                if (resp.getProviderId() != null) {
                    resp.setProviderName(providerNames.get(resp.getProviderId()));
                }
            });
        }
        return page;
    }

    @Override
    public CostumeAdminResponse getById(Integer id) {
        CostumeAdminResponse resp = super.getByIdEntity(id);
        if (resp != null && resp.getProviderId() != null) {
            providerRepository.findById(resp.getProviderId())
                    .ifPresent(p -> resp.setProviderName(p.getShopName()));
        }
        return resp;
    }

    @Override
    public CostumeAdminResponse create(CostumeAdminResponse request) {
        return super.createEntity(request);
    }

    @Override
    public CostumeAdminResponse update(Integer id, CostumeAdminResponse request) {
        return super.updateEntity(id, request);
    }

    @Override
    public void delete(Integer id) {
        super.deleteEntity(id);
    }
}
