package com.cosmate.service;

import com.cosmate.dto.response.AdminAuditLogResponse;
import com.cosmate.entity.AdminAuditLog;

import java.util.List;

public interface AdminAuditLogService {
    void log(String actor, String action, String entityType, String entityId, String detail);
    List<AdminAuditLogResponse> findAll();
}
