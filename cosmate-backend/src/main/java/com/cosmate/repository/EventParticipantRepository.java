package com.cosmate.repository;

import com.cosmate.entity.EventParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventParticipantRepository extends JpaRepository<EventParticipant, Integer> {
    // Lấy danh sách bài dự thi của một sự kiện cụ thể
    List<EventParticipant> findByEventId(Integer eventId);

    // Kiểm tra xem user này đã nộp bài thi cho sự kiện này chưa (Chống spam nộp nhiều bài)
    boolean existsByEventIdAndCosplayerId(Integer eventId, Integer cosplayerId);
}