package com.cosmate.controller;

import com.cosmate.base.crud.BaseCrudDataIoController;
import com.cosmate.base.crud.BaseCrudRepository;
import com.cosmate.base.crud.CrudService;
import com.cosmate.dto.filter.CostumeFilter;
import com.cosmate.dto.response.CostumeAdminResponse;
import com.cosmate.entity.Costume;
import com.cosmate.service.CostumeAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/costumes")
@RequiredArgsConstructor
public class CostumeAdminController extends BaseCrudDataIoController<Costume, Integer, CostumeAdminResponse, CostumeFilter> {

    private final CostumeAdminService costumeAdminService;

    @PostMapping("/migrate-cost-from-deposit")
    public Map<String, Object> migrateCostFromDeposit() {
        costumeAdminService.migrateCostFromDeposit();
        return Map.of("status", "ok");
    }

    @Override
    protected CrudService<Integer, CostumeAdminResponse, CostumeFilter> getService() {
        return costumeAdminService;
    }

    @Override
    protected BaseCrudRepository<Costume, Integer> getRepository() {
        return costumeAdminService.getRepository();
    }

    @Override
    protected Class<Costume> getEntityClass() {
        return Costume.class;
    }
}
