package com.cosmate.repository;

import com.cosmate.entity.AiTokenSubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiTokenSubscriptionPlanRepository extends JpaRepository<AiTokenSubscriptionPlan, Integer> {
    List<AiTokenSubscriptionPlan> findByIsActiveTrue();
}

