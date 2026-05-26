package com.cosmate.service.impl;

import com.cosmate.dto.request.system.SystemConfigUpdateRequest;
import com.cosmate.entity.SystemConfig;
import com.cosmate.exception.AppException;
import com.cosmate.exception.ErrorCode;
import com.cosmate.repository.SystemConfigRepository;
import com.cosmate.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;

    @Override
    public List<SystemConfig> getAllConfigs() {
        return systemConfigRepository.findAll();
    }

    @Override
    public SystemConfig updateConfig(String configKey, SystemConfigUpdateRequest request) {
        SystemConfig config = systemConfigRepository.findById(configKey)
                .orElseThrow(() -> new RuntimeException("System config not found with key: " + configKey));
        
        config.setConfigValue(request.getConfigValue());
        return systemConfigRepository.save(config);
    }
}
