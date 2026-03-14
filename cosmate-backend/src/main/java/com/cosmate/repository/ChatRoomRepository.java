package com.cosmate.repository;

import com.cosmate.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Integer> {
    // Tìm phòng chat giữa 2 người, không quan tâm thứ tự
    @Query("SELECT c FROM ChatRoom c WHERE (c.user1Id = :userId1 AND c.user2Id = :userId2) OR (c.user1Id = :userId2 AND c.user2Id = :userId1)")
    Optional<ChatRoom> findRoomByUsers(Integer userId1, Integer userId2);
}