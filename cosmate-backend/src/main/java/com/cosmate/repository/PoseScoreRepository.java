package com.cosmate.repository;

import com.cosmate.entity.PoseScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PoseScoreRepository extends JpaRepository<PoseScore, Integer> {
    // Lấy tất cả lịch sử của user
    List<PoseScore> findByCosplayerIdOrderByCreatedAtDesc(Integer cosplayerId);

    // Lấy lịch sử CÓ TÌM KIẾM theo tên nhân vật
    List<PoseScore> findByCosplayerIdAndCharacterNameContainingIgnoreCaseOrderByCreatedAtDesc(Integer cosplayerId, String characterName);
}