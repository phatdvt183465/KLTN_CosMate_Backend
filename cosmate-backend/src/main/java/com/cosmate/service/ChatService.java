package com.cosmate.service;

import com.cosmate.dto.request.ChatMessageRequest;
import com.cosmate.dto.response.ChatMessageResponse;
import com.cosmate.dto.response.ChatPartnerProfileResponse;
import com.cosmate.entity.ChatMessage;
import com.cosmate.entity.ChatRoom;

import java.util.List;

public interface ChatService {
    ChatRoom getOrCreateRoom(Integer user1Id, Integer user2Id);
    ChatMessage saveMessageAndBroadcast(ChatMessageRequest request);
    List<ChatMessageResponse> getMessageHistory(Integer roomId);
    ChatPartnerProfileResponse getPartnerProfile(Integer roomId, Integer currentUserId);
}