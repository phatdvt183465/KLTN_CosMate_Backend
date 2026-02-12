package com.cosmate.repository;

import com.cosmate.entity.OrderImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderImageRepository extends JpaRepository<OrderImage, Integer> {
    List<OrderImage> findByOrderId(Integer orderId);
}

