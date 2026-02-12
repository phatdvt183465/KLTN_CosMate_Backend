package com.cosmate.repository;

import com.cosmate.entity.OrderDetailAccessory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderDetailAccessoryRepository extends JpaRepository<OrderDetailAccessory, Integer> {
    List<OrderDetailAccessory> findByOrderDetailId(Integer orderDetailId);
    List<OrderDetailAccessory> findByOrderDetailIdIn(List<Integer> orderDetailIds);
}
