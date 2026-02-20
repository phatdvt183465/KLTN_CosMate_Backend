package com.cosmate.repository;

import com.cosmate.entity.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DisputeRepository extends JpaRepository<Dispute, Integer> {
    List<Dispute> findByOrderId(Integer orderId);
    List<Dispute> findByCreatedByUserIdOrderByCreatedAtDesc(Integer userId);
    List<Dispute> findByStatusOrderByCreatedAtDesc(String status);
}

