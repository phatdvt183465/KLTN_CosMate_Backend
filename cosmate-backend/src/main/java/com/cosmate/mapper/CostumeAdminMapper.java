package com.cosmate.mapper;

import com.cosmate.entity.Costume;
import com.cosmate.dto.response.CostumeAdminResponse;
import com.cosmate.base.crud.BaseCrudMapper;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.Builder;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface CostumeAdminMapper extends BaseCrudMapper<Costume, CostumeAdminResponse> {

    @Override
    CostumeAdminResponse toResponse(Costume entity);

    @Override
    Costume toEntity(CostumeAdminResponse dto);

    @Override
    void update(@MappingTarget Costume entity, CostumeAdminResponse dto);

}
