package com.cosmate.service;

import com.cosmate.base.crud.CrudService;
import com.cosmate.base.crud.BaseCrudRepository;
import com.cosmate.dto.filter.CostumeFilter;
import com.cosmate.dto.response.CostumeAdminResponse;
import com.cosmate.entity.Costume;

public interface CostumeAdminService extends CrudService<Integer, CostumeAdminResponse, CostumeFilter> {
    BaseCrudRepository<Costume, Integer> getRepository();
    
    /**
     * Migrate existing data so that cost equals depositAmount for existing costumes.
     * Only updates costumes where depositAmount is present and cost is null or different.
     */
    void migrateCostFromDeposit();
}
