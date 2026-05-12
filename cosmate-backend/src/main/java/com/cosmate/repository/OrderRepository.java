package com.cosmate.repository;

import com.cosmate.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import com.cosmate.base.crud.BaseCrudRepository;

@Repository
public interface OrderRepository extends BaseCrudRepository<Order, Integer> {
    List<Order> findByCosplayerIdOrderByCreatedAtDesc(Integer cosplayerId);
    List<Order> findByProviderIdOrderByCreatedAtDesc(Integer providerId);
    List<Order> findAllByOrderByCreatedAtDesc();
    // find orders by exact status
    List<Order> findByStatus(String status);
    List<Order> findByProviderIdAndStatusInOrderByCreatedAtDesc(Integer providerId, List<String> statuses);
    List<Order> findByOrderTypeAndStatusInOrderByCreatedAtDesc(String orderType, List<String> statuses);
    List<Order> findByProviderIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(Integer providerId, String orderType, List<String> statuses);
    List<Order> findByCosplayerIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(Integer cosplayerId, String orderType, List<String> statuses);
}
