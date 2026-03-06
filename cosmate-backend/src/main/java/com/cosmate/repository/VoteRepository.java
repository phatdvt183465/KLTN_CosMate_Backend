package com.cosmate.repository;

import com.cosmate.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VoteRepository extends JpaRepository<Vote, Integer> {
    // Kiểm tra xem User này đã vote cho Bài dự thi này chưa (Chống gian lận vote nhiều lần)
    boolean existsByParticipantIdAndVoterId(Integer participantId, Integer voterId);
}