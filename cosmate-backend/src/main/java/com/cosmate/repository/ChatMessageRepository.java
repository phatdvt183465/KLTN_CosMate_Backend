package com.cosmate.repository;

import com.cosmate.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Integer> {
    // Lấy lịch sử chat của 1 phòng, sắp xếp cũ nhất lên trước
    List<ChatMessage> findByRoomIdOrderByCreatedAtAsc(Integer roomId);
}