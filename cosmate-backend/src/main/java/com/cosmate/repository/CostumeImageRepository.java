package com.cosmate.repository;

import com.cosmate.entity.CostumeImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CostumeImageRepository extends JpaRepository<CostumeImage, Long> {
    List<CostumeImage> findByCostumeId(Long costumeId);
}