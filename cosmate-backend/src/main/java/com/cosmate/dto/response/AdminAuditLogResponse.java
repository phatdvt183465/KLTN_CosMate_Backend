package com.cosmate.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditLogResponse {
    private String id;
    private String actor;
    private String action;
    private String entityType;
    private String entityId;
    private String detail;
    private LocalDateTime createdAt;
}
