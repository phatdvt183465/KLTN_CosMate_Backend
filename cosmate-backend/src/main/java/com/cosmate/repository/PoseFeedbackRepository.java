package com.cosmate.repository;

import com.cosmate.entity.PoseFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PoseFeedbackRepository extends JpaRepository<PoseFeedback, Integer> {
}
