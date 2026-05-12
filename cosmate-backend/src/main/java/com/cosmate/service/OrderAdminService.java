package com.cosmate.service;

import com.cosmate.base.crud.CrudService;
import com.cosmate.base.crud.BaseCrudRepository;
import com.cosmate.dto.filter.OrderFilter;
import com.cosmate.dto.response.OrderAdminResponse;
import com.cosmate.entity.Order;

public interface OrderAdminService extends CrudService<Integer, OrderAdminResponse, OrderFilter> {
    BaseCrudRepository<Order, Integer> getRepository();
}
