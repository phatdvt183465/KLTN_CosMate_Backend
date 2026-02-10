package com.cosmate.repository;

import com.cosmate.entity.OrderAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderAddressRepository extends JpaRepository<OrderAddress, Integer> {
    List<OrderAddress> findByOrderId(Integer orderId);
}

