package com.cosmate.service;

import com.cosmate.dto.request.ChatMessageRequest;
import com.cosmate.dto.response.ChatMessageResponse;
import com.cosmate.dto.response.ChatPartnerProfileResponse;
import com.cosmate.dto.response.ChatRoomResponse;
import com.cosmate.entity.ChatMessage;
import com.cosmate.entity.ChatRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ChatService {
    ChatRoom getOrCreateRoom(Integer user1Id, Integer user2Id);

    ChatMessage saveMessageAndBroadcast(ChatMessageRequest request);

    ChatMessageResponse sendMessage(ChatMessageRequest request);

    Page<ChatMessageResponse> getMessageHistory(Integer roomId, Pageable pageable);

    ChatPartnerProfileResponse getPartnerProfile(Integer roomId, Integer currentUserId);

    List<ChatRoomResponse> getUserChatRooms(Integer userId);

    void markMessagesAsRead(Integer roomId, Integer currentUserId);

    int getTotalUnreadCount(Integer userId);

    String uploadChatImage(MultipartFile file, Integer roomId) throws Exception;
}