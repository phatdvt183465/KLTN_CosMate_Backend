package com.cosmate.repository;

import com.cosmate.entity.OrderCostumeSurcharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderCostumeSurchargeRepository extends JpaRepository<OrderCostumeSurcharge, Integer> {
    List<OrderCostumeSurcharge> findByOrderId(Integer orderId);
}

