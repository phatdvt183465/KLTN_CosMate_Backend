package com.cosmate.mapper;

import com.cosmate.entity.Order;
import com.cosmate.dto.response.OrderAdminResponse;
import com.cosmate.base.crud.BaseCrudMapper;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface OrderAdminMapper extends BaseCrudMapper<Order, OrderAdminResponse> {

    @Override
    OrderAdminResponse toResponse(Order entity);

    @Override
    Order toEntity(OrderAdminResponse dto);

    @Override
    void update(@MappingTarget Order entity, OrderAdminResponse dto);

}
