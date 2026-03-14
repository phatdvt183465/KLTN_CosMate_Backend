package com.cosmate.dto.request;
import lombok.Data;

@Data
public class ChatMessageRequest {
    private Integer roomId;
    private Integer senderId;
    private String messageType;
    private String content;
}