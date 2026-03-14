package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ChatMessageResponse {
    private Integer id;
    private Integer roomId;
    private Integer senderId;
    private String messageType;
    private String content;
    private LocalDateTime createdAt;
    private Boolean isRead;
}