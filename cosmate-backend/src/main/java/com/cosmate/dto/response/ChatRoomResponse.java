package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ChatRoomResponse {
    private Integer roomId;
    private Integer partnerId;
    private String partnerName;
    private String partnerAvatar;
    private LocalDateTime lastMessageAt;
    private Integer unreadCount;
}