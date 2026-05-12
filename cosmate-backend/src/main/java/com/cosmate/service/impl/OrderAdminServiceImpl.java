package com.cosmate.service.impl;

import com.cosmate.base.crud.BaseCrudMapper;
import com.cosmate.base.crud.BaseCrudRepository;
import com.cosmate.base.crud.CrudServiceImpl;
import com.cosmate.dto.filter.OrderFilter;
import com.cosmate.dto.response.OrderAdminResponse;
import com.cosmate.entity.Order;
import com.cosmate.entity.Provider;
import com.cosmate.entity.User;
import com.cosmate.mapper.OrderAdminMapper;
import com.cosmate.repository.OrderRepository;
import com.cosmate.repository.ProviderRepository;
import com.cosmate.repository.UserRepository;
import com.cosmate.service.OrderAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderAdminServiceImpl extends CrudServiceImpl<Order, Integer, OrderAdminResponse, OrderFilter> implements OrderAdminService {

    private final OrderRepository orderRepository;
    private final OrderAdminMapper orderAdminMapper;
    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;

    @Override
    public BaseCrudRepository<Order, Integer> getRepository() {
        return orderRepository;
    }

    @Override
    protected BaseCrudMapper<Order, OrderAdminResponse> getMapper() {
        return orderAdminMapper;
    }

    @Override
    protected String[] searchableFields() {
        return new String[]{"status", "orderType"};
    }

    @Override
    public Page<OrderAdminResponse> getAll(Pageable pageable, String search, OrderFilter filter) {
        Page<OrderAdminResponse> page = super.getAllEntity(pageable, search, filter);
        populateNames(page.getContent());
        return page;
    }

    @Override
    public OrderAdminResponse getById(Integer id) {
        OrderAdminResponse resp = super.getByIdEntity(id);
        if (resp != null) {
            populateNames(List.of(resp));
        }
        return resp;
    }

    private void populateNames(List<OrderAdminResponse> list) {
        if (list == null || list.isEmpty()) return;

        List<Integer> providerIds = list.stream().map(OrderAdminResponse::getProviderId).filter(id -> id != null).distinct().collect(Collectors.toList());
        List<Integer> cosplayerIds = list.stream().map(OrderAdminResponse::getCosplayerId).filter(id -> id != null).distinct().collect(Collectors.toList());

        Map<Integer, String> providerNames = providerIds.isEmpty() ? Map.of() : providerRepository.findAllById(providerIds)
                .stream().collect(Collectors.toMap(Provider::getId, Provider::getShopName));
        Map<Integer, String> userNames = cosplayerIds.isEmpty() ? Map.of() : userRepository.findAllById(cosplayerIds)
                .stream().collect(Collectors.toMap(User::getId, u -> u.getFullName() != null ? u.getFullName() : u.getUsername()));

        for (OrderAdminResponse resp : list) {
            if (resp.getProviderId() != null) {
                resp.setProviderName(providerNames.get(resp.getProviderId()));
            }
            if (resp.getCosplayerId() != null) {
                resp.setUserName(userNames.get(resp.getCosplayerId()));
                resp.setCosplayerName(userNames.get(resp.getCosplayerId()));
            }
            if (resp.getId() != null) {
                resp.setCode("ORD-" + resp.getId());
            }
        }
    }

    @Override
    public OrderAdminResponse create(OrderAdminResponse request) {
        return super.createEntity(request);
    }

    @Override
    public OrderAdminResponse update(Integer id, OrderAdminResponse request) {
        return super.updateEntity(id, request);
    }

    @Override
    public void delete(Integer id) {
        super.deleteEntity(id);
    }
}
