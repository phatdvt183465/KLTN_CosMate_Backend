package com.cosmate.service.impl;

import com.cosmate.dto.request.ChatMessageRequest;
import com.cosmate.dto.response.ChatMessageResponse;
import com.cosmate.entity.ChatMessage;
import com.cosmate.entity.ChatRoom;
import com.cosmate.repository.ChatMessageRepository;
import com.cosmate.repository.ChatRoomRepository;
import com.cosmate.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public ChatRoom getOrCreateRoom(Integer user1Id, Integer user2Id) {
        // ... (Giữ nguyên logic cũ)
        return chatRoomRepository.findRoomByUsers(user1Id, user2Id).orElseGet(() -> {
            ChatRoom newRoom = new ChatRoom();
            newRoom.setUser1Id(Math.min(user1Id, user2Id));
            newRoom.setUser2Id(Math.max(user1Id, user2Id));
            newRoom.setLastMessageAt(LocalDateTime.now());
            return chatRoomRepository.save(newRoom);
        });
    }

    // THÊM MỚI: Lấy lịch sử chat
    @Override
    public List<ChatMessageResponse> getMessageHistory(Integer roomId) {
        List<ChatMessage> messages = chatMessageRepository.findByRoomIdOrderByCreatedAtAsc(roomId);
        return messages.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    public ChatMessage saveMessageAndBroadcast(ChatMessageRequest request) {
        ChatMessage newMessage = new ChatMessage();
        newMessage.setRoomId(request.getRoomId());
        newMessage.setSenderId(request.getSenderId());
        newMessage.setMessageType(request.getMessageType());
        newMessage.setContent(request.getContent());
        newMessage.setIsRead(false);
        ChatMessage savedMessage = chatMessageRepository.save(newMessage);

        chatRoomRepository.findById(request.getRoomId()).ifPresent(room -> {
            room.setLastMessageAt(LocalDateTime.now());
            chatRoomRepository.save(room);
        });

        // BẮN DTO RESPONSE LÊN WEBSOCKET THAY VÌ RAW ENTITY
        ChatMessageResponse responseDto = mapToResponse(savedMessage);
        messagingTemplate.convertAndSend("/topic/room/" + request.getRoomId(), responseDto);

        return savedMessage;
    }

    // Hàm support map Entity -> DTO
    private ChatMessageResponse mapToResponse(ChatMessage message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .roomId(message.getRoomId())
                .senderId(message.getSenderId())
                .messageType(message.getMessageType())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .isRead(message.getIsRead())
                .build();
    }
}