package com.cosmate.repository;

import com.cosmate.entity.PoseFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PoseFeedbackRepository extends JpaRepository<PoseFeedback, Integer> {
    List<PoseFeedback> findByStatus(String status);

    boolean existsByUser_IdAndPoseScore_Id(Integer userId, Integer poseScoreId);
}
