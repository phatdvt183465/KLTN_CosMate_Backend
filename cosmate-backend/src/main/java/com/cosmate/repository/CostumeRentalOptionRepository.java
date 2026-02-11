package com.cosmate.repository;
import com.cosmate.entity.CostumeRentalOption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CostumeRentalOptionRepository extends JpaRepository<CostumeRentalOption, Integer> {
    List<CostumeRentalOption> findByCostumeId(Integer costumeId);
}