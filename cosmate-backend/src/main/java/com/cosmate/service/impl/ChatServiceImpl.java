package com.cosmate.service.impl;

import com.cosmate.dto.request.ChatMessageRequest;
import com.cosmate.dto.response.ChatMessageResponse;
import com.cosmate.dto.response.ChatPartnerProfileResponse;
import com.cosmate.dto.response.ChatRoomResponse;
import com.cosmate.entity.ChatMessage;
import com.cosmate.entity.ChatRoom;
import com.cosmate.entity.Provider;
import com.cosmate.repository.ChatMessageRepository;
import com.cosmate.repository.ChatRoomRepository;
import com.cosmate.repository.ProviderRepository;
import com.cosmate.repository.UserRepository;
import com.cosmate.service.ChatService;
import com.cosmate.service.FirebaseStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.cosmate.entity.Notification;
import com.cosmate.entity.User;
import com.cosmate.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final NotificationService notificationService;
    private final FirebaseStorageService firebaseStorageService;

    @Override
    public ChatRoom getOrCreateRoom(Integer user1Id, Integer user2Id) {
        if (user1Id.equals(user2Id)) throw new IllegalArgumentException("Không thể tạo phòng chat với chính mình!");
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
    public Page<ChatMessageResponse> getMessageHistory(Integer roomId, Pageable pageable) {
        return chatMessageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional
    public ChatMessage saveMessageAndBroadcast(ChatMessageRequest request) {
        ChatMessage savedMessage = persistAndBroadcast(request);
        return savedMessage;
    }

    @Override
    @Transactional
    public ChatMessageResponse sendMessage(ChatMessageRequest request) {
        ChatMessage savedMessage = persistAndBroadcast(request);
        return mapToResponse(savedMessage);
    }

    private ChatMessage persistAndBroadcast(ChatMessageRequest request) {
        if (request.getRoomId() == null || request.getSenderId() == null) {
            throw new IllegalArgumentException("roomId và senderId không được để trống");
        }

        ChatRoom room = chatRoomRepository.findById(request.getRoomId()).orElseThrow();

        ChatMessage newMessage = new ChatMessage();
        newMessage.setRoomId(request.getRoomId());
        newMessage.setSenderId(request.getSenderId());
        newMessage.setMessageType(request.getMessageType());
        newMessage.setContent(request.getContent());
        newMessage.setIsRead(false);
        ChatMessage savedMessage = chatMessageRepository.save(newMessage);

        room.setLastMessageAt(LocalDateTime.now());
        chatRoomRepository.save(room);

        ChatMessageResponse responseDto = mapToResponse(savedMessage);
        messagingTemplate.convertAndSend("/topic/room/" + request.getRoomId(), responseDto);

        Integer receiverId = room.getUser1Id().equals(request.getSenderId()) ? room.getUser2Id() : room.getUser1Id();
        String senderName = getDisplayName(request.getSenderId());

        Notification noti = Notification.builder()
                .user(User.builder().id(receiverId).build())
                .type("CHAT_MESSAGE")
                .header(senderName + " đã gửi tin nhắn cho bạn")
                .content(request.getContent())
                .sendAt(LocalDateTime.now())
                .isRead(false)
                .build();
        notificationService.create(noti);

        return savedMessage;
    }

    // Hàm support map Entity -> DTO
    private String getDisplayName(Integer userId) {
        var provOpt = providerRepository.findByUserId(userId);
        if (provOpt.isPresent() && provOpt.get().getShopName() != null) return provOpt.get().getShopName();
        return userRepository.findById(userId).map(User::getFullName).orElse("Ai đó");
    }

    private ChatMessageResponse mapToResponse(ChatMessage message) {
        return ChatMessageResponse.builder()
                .id(message.getId()).roomId(message.getRoomId()).senderId(message.getSenderId())
                .messageType(message.getMessageType()).content(message.getContent())
                .createdAt(message.getCreatedAt()).isRead(message.getIsRead())
                .build();
    }

    @Override
    public ChatPartnerProfileResponse getPartnerProfile(Integer roomId, Integer currentUserId) {
        ChatRoom room = chatRoomRepository.findById(roomId).orElseThrow();
        Integer partnerId = room.getUser1Id().equals(currentUserId) ? room.getUser2Id() : room.getUser1Id();

        String avatar = userRepository.findById(partnerId).map(User::getAvatarUrl).orElse(null);
        var provOpt = providerRepository.findByUserId(partnerId);
        if (provOpt.isPresent() && provOpt.get().getAvatarUrl() != null) {
            avatar = provOpt.get().getAvatarUrl();
        }

        return ChatPartnerProfileResponse.builder()
                .partnerId(partnerId)
                .fullName(getDisplayName(partnerId))
                .avatarUrl(avatar)
                .build();
    }

    @Override
    public List<ChatRoomResponse> getUserChatRooms(Integer userId) {
        List<ChatRoom> rooms = chatRoomRepository.findAllRoomsByUserId(userId);

        return rooms.stream().map(room -> {
            Integer partnerId = room.getUser1Id().equals(userId) ? room.getUser2Id() : room.getUser1Id();

            String partnerName = "Người dùng ẩn danh";
            String partnerAvatar = null;

            var partnerOpt = userRepository.findById(partnerId);
            if (partnerOpt.isPresent()) {
                partnerName = partnerOpt.get().getFullName();
                partnerAvatar = partnerOpt.get().getAvatarUrl();

                // Tương tự, ưu tiên lấy tên và avatar của Shop
                Optional<Provider> providerOpt = providerRepository.findByUserId(partnerId);
                if (providerOpt.isPresent()) {
                    Provider provider = providerOpt.get();
                    if (provider.getShopName() != null && !provider.getShopName().isBlank()) {
                        partnerName = provider.getShopName();
                    }
                    if (provider.getAvatarUrl() != null && !provider.getAvatarUrl().isBlank()) {
                        partnerAvatar = provider.getAvatarUrl();
                    }
                }
            }

            int unread = chatMessageRepository.countByRoomIdAndSenderIdNotAndIsReadFalse(room.getId(), userId);

            return ChatRoomResponse.builder()
                    .roomId(room.getId())
                    .partnerId(partnerId)
                    .partnerName(partnerName)
                    .partnerAvatar(partnerAvatar)
                    .lastMessageAt(room.getLastMessageAt())
                    .unreadCount(unread)
                    .build();
        }).collect(Collectors.toList());
    }

    // ĐÁNH DẤU ĐÃ ĐỌC
    @Override
    @Transactional
    public void markMessagesAsRead(Integer roomId, Integer currentUserId) {
        chatMessageRepository.markPartnerMessagesAsRead(roomId, currentUserId);
    }

    @Override
    public int getTotalUnreadCount(Integer userId) {
        return chatMessageRepository.countTotalUnreadMessages(userId);
    }

    @Override
    public String uploadChatImage(MultipartFile file, Integer roomId) throws Exception {
        // Tạo đường dẫn lưu trên Firebase: ví dụ "chat_images/1/1712764800_photo.jpg"
        String pathInBucket = "chat_images/" + roomId + "/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();

        // Tận dụng chính hàm uploadFile bá đạo của ông
        return firebaseStorageService.uploadFile(file, pathInBucket);
    }
}