package com.cosmate.controller;

import com.cosmate.base.crud.BaseCrudDataIoController;
import com.cosmate.base.crud.BaseCrudRepository;
import com.cosmate.base.crud.CrudService;
import com.cosmate.dto.filter.OrderFilter;
import com.cosmate.dto.response.OrderAdminResponse;
import com.cosmate.entity.Order;
import com.cosmate.service.OrderAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class OrderAdminController extends BaseCrudDataIoController<Order, Integer, OrderAdminResponse, OrderFilter> {

    private final OrderAdminService orderAdminService;

    @Override
    protected CrudService<Integer, OrderAdminResponse, OrderFilter> getService() {
        return orderAdminService;
    }

    @Override
    protected BaseCrudRepository<Order, Integer> getRepository() {
        return orderAdminService.getRepository();
    }

    @Override
    protected Class<Order> getEntityClass() {
        return Order.class;
    }
}
