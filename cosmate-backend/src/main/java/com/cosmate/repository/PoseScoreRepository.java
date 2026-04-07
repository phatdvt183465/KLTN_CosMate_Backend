package com.cosmate.repository;

import com.cosmate.entity.PoseScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PoseScoreRepository extends JpaRepository<PoseScore, Integer> {
    // Thêm dòng này để lấy lịch sử của 1 user, sắp xếp mới nhất lên đầu
    List<PoseScore> findByCosplayerIdOrderByCreatedAtDesc(Integer cosplayerId);
}