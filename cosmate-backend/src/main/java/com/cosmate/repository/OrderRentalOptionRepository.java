package com.cosmate.repository;

import com.cosmate.entity.OrderRentalOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRentalOptionRepository extends JpaRepository<OrderRentalOption, Integer> {
    List<OrderRentalOption> findByOrderDetailId(Integer orderDetailId);
    List<OrderRentalOption> findByOrderDetailIdIn(List<Integer> orderDetailIds);
}
