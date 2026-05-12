package com.cosmate.mapper;

import com.cosmate.entity.Provider;
import com.cosmate.dto.response.ProviderResponse;
import com.cosmate.base.crud.BaseCrudMapper;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.Builder;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface ProviderAdminMapper extends BaseCrudMapper<Provider, ProviderResponse> {

    @Override
    ProviderResponse toResponse(Provider entity);

    @Override
    Provider toEntity(ProviderResponse dto);

    @Override
    void update(@MappingTarget Provider entity, ProviderResponse dto);

}
