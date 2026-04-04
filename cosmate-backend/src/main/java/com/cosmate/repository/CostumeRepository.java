package com.cosmate.repository;

import com.cosmate.entity.Costume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CostumeRepository extends JpaRepository<Costume, Integer> {
    List<Costume> findByProviderIdAndStatusNotIgnoreCase(Integer providerId, String status);
    List<Costume> findByNameContainingIgnoreCaseAndStatusNot(String keyword, String status);

    @Query("SELECT c FROM Costume c WHERE c.costumeVector IS NOT NULL")
    List<Costume> findAllWithVector();
}