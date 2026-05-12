package com.cosmate.service;

import com.cosmate.dto.response.AdminAuditLogResponse;
import com.cosmate.entity.AdminAuditLog;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminAuditLogService {
    void log(String actor, String action, String entityType, String entityId, String detail);
    Page<AdminAuditLogResponse> findAll(Pageable pageable);
}
