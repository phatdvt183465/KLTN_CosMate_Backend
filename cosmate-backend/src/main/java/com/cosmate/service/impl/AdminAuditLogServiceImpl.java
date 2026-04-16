package com.cosmate.service.impl;

import com.cosmate.dto.response.AdminAuditLogResponse;
import com.cosmate.entity.AdminAuditLog;
import com.cosmate.repository.AdminAuditLogRepository;
import com.cosmate.service.AdminAuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAuditLogServiceImpl implements AdminAuditLogService {

    private final AdminAuditLogRepository repository;

    @Override
    public void log(String actor, String action, String entityType, String entityId, String detail) {
        repository.save(AdminAuditLog.builder()
                .actor(actor)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .detail(detail)
                .build());
    }

    @Override
    public List<AdminAuditLogResponse> findAll() {
        return repository.findAll().stream().map(log -> AdminAuditLogResponse.builder()
                .id(String.valueOf(log.getId()))
                .actor(log.getActor())
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .detail(log.getDetail())
                .createdAt(log.getCreatedAt())
                .build()).toList();
    }
}
