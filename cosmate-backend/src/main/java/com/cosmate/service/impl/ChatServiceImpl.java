package com.cosmate.service.impl;

import com.cosmate.dto.request.ChatMessageRequest;
import com.cosmate.dto.response.ChatMessageResponse;
import com.cosmate.dto.response.ChatPartnerProfileResponse;
import com.cosmate.dto.response.ChatRoomResponse;
import com.cosmate.entity.ChatMessage;
import com.cosmate.entity.ChatRoom;
import com.cosmate.repository.ChatMessageRepository;
import com.cosmate.repository.ChatRoomRepository;
import com.cosmate.repository.UserRepository;
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
    private final UserRepository userRepository;

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
    @Override
    public ChatPartnerProfileResponse getPartnerProfile(Integer roomId, Integer currentUserId) {
        // 1. Lấy phòng chat ra
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng chat!"));

        // 2. Tìm ID của người đối diện
        Integer partnerId;
        if (room.getUser1Id().equals(currentUserId)) {
            partnerId = room.getUser2Id();
        } else if (room.getUser2Id().equals(currentUserId)) {
            partnerId = room.getUser1Id();
        } else {
            throw new RuntimeException("Bạn không thuộc phòng chat này!");
        }

        // 3. Lấy thông tin Tên và Avatar từ bảng Users
        // Giả sử class User của ông có các hàm getFullName() và getAvatarUrl()
        return userRepository.findById(partnerId)
                .map(user -> ChatPartnerProfileResponse.builder()
                        .partnerId(user.getId())
                        .fullName(user.getFullName())
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin đối tác!"));
    }

    @Override
    public List<ChatRoomResponse> getUserChatRooms(Integer userId) {
        // Lấy tất cả các phòng của user này
        List<ChatRoom> rooms = chatRoomRepository.findAllRoomsByUserId(userId);

        // Map sang DTO để trả cho frontend
        return rooms.stream().map(room -> {
            // Lấy ID của người đối diện
            Integer partnerId = room.getUser1Id().equals(userId) ? room.getUser2Id() : room.getUser1Id();

            // Tìm thông tin của người đối diện (tránh lỗi null nếu lỡ DB chưa có)
            String partnerName = "Người dùng ẩn danh";
            String partnerAvatar = null;

            var partnerOpt = userRepository.findById(partnerId);
            if (partnerOpt.isPresent()) {
                partnerName = partnerOpt.get().getFullName();
                partnerAvatar = partnerOpt.get().getAvatarUrl();
            }

            return ChatRoomResponse.builder()
                    .roomId(room.getId())
                    .partnerId(partnerId)
                    .partnerName(partnerName)
                    .partnerAvatar(partnerAvatar)
                    .lastMessageAt(room.getLastMessageAt())
                    .build();
        }).collect(Collectors.toList());
    }
}