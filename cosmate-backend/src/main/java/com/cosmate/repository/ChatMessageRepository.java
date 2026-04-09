package com.cosmate.repository;

import com.cosmate.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Integer> {
    // Lấy tin nhắn mới nhất trước (DESC) để phân trang dễ hơn, frontend sẽ đảo ngược lại sau
    Page<ChatMessage> findByRoomIdOrderByCreatedAtDesc(Integer roomId, Pageable pageable);

    // Đếm tin nhắn chưa đọc của đối tác
    int countByRoomIdAndSenderIdNotAndIsReadFalse(Integer roomId, Integer currentUserId);

    // Cập nhật trạng thái đã đọc
    @Modifying
    @Query("UPDATE ChatMessage m SET m.isRead = true WHERE m.roomId = :roomId AND m.senderId != :currentUserId AND m.isRead = false")
    void markPartnerMessagesAsRead(Integer roomId, Integer currentUserId);

    // Trong ChatMessageRepository.java
    @Query("SELECT COUNT(m) FROM ChatMessage m " +
            "WHERE m.roomId IN (SELECT r.id FROM ChatRoom r WHERE r.user1Id = :userId OR r.user2Id = :userId) " +
            "AND m.senderId != :userId AND m.isRead = false")
    int countTotalUnreadMessages(@Param("userId") Integer userId);
}