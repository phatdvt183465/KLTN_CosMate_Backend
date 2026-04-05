package com.cosmate.repository;

import com.cosmate.entity.OrderDetailExtend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderDetailExtendRepository extends JpaRepository<OrderDetailExtend, Integer> {
    List<OrderDetailExtend> findByOrderDetailId(Integer orderDetailId);
}

