package com.cosmate.base.crud;

import org.mapstruct.MappingTarget;

public interface BaseCrudMapper<E, D> {

    D toResponse(E entity);

    E toEntity(D dto);

    void update(@MappingTarget E entity, D dto);
}
