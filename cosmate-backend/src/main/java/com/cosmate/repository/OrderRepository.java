package com.cosmate.repository;

import com.cosmate.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {
    List<Order> findByCosplayerIdOrderByCreatedAtDesc(Integer cosplayerId);
    List<Order> findByProviderIdOrderByCreatedAtDesc(Integer providerId);
    List<Order> findAllByOrderByCreatedAtDesc();
    List<Order> findByProviderIdAndStatusInOrderByCreatedAtDesc(Integer providerId, List<String> statuses);
}
