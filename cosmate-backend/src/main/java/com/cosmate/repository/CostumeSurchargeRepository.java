package com.cosmate.repository;

import com.cosmate.entity.CostumeSurcharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CostumeSurchargeRepository extends JpaRepository<CostumeSurcharge, Long> {
    List<CostumeSurcharge> findByCostumeId(Long costumeId);
}