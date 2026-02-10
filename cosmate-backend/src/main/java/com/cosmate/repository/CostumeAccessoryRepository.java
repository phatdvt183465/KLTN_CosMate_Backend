package com.cosmate.repository;
import com.cosmate.entity.CostumeAccessory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CostumeAccessoryRepository extends JpaRepository<CostumeAccessory, Integer> {
    List<CostumeAccessory> findByCostumeId(Integer costumeId);
}