package com.cosmate.repository;

import com.cosmate.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Integer> {
    List<Review> findByOrderId(Integer orderId);

    // find reviews whose order belongs to a provider (uses nested property traversal)
    List<Review> findByOrderProviderId(Integer providerId);
}
