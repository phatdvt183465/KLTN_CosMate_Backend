package com.cosmate.repository;

import com.cosmate.entity.DisputeImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DisputeImageRepository extends JpaRepository<DisputeImage, Integer> {
    List<DisputeImage> findByDisputeId(Integer disputeId);
}

