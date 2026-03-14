package com.cosmate.repository;

import com.cosmate.entity.WishlistCostume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishlistRepository extends JpaRepository<WishlistCostume, Integer> {
    List<WishlistCostume> findAllByUserId(Integer userId);
    Optional<WishlistCostume> findByUserIdAndCostumeId(Integer userId, Integer costumeId);
}

