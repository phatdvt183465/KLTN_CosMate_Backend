package com.cosmate.repository;

import com.cosmate.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Integer> {
    List<Review> findByOrderId(Integer orderId);

    // find reviews whose order belongs to a provider (uses nested property traversal)
    List<Review> findByOrderProviderId(Integer providerId);

    // find reviews for a costume by joining the reviews' orders with the order-costume mapping table
    // Find reviews for a costume by joining order details (which reference costumes) with reviews by order_id
    @Query(value = "SELECT DISTINCT r.* FROM Reviews r JOIN Order_Detail od ON od.order_id = r.order_id WHERE od.costume_id = ?1", nativeQuery = true)
    List<Review> findByCostumeId(Integer costumeId);
}
