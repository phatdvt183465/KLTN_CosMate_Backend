package com.cosmate.repository;

import com.cosmate.entity.OrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderDetailRepository extends JpaRepository<OrderDetail, Integer> {
    List<OrderDetail> findByOrderId(Integer orderId);

    // Dùng Native Query của SQL Server để lấy TOP
    @Query(value = "SELECT TOP (:limit) od.costume_id FROM Order_Detail od " +
            "INNER JOIN Orders o ON od.order_id = o.id " +
            "INNER JOIN Users u ON o.cosplayer_id = u.id " +
            "WHERE u.current_archetype = :archetype " +
            "GROUP BY od.costume_id " +
            "ORDER BY COUNT(od.costume_id) DESC", nativeQuery = true)
    java.util.List<Integer> findTopCostumeIdsByArchetype(@org.springframework.data.repository.query.Param("archetype") String archetype, @org.springframework.data.repository.query.Param("limit") int limit);
}
