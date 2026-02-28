package com.cosmate.repository;

import com.cosmate.entity.CostumeImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CostumeImageRepository extends JpaRepository<CostumeImage, Integer> {
    List<CostumeImage> findByCostumeId(Integer costumeId);
    @Query("SELECT c FROM CostumeImage c WHERE c.imageVector IS NOT NULL")
    List<CostumeImage> findAllWithVector();
}