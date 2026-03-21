package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse {
    private Integer id;
    private Integer userId;
    private String type;
    private String header;
    private String content;
    private LocalDateTime sendAt;
    private Boolean isRead;
}

