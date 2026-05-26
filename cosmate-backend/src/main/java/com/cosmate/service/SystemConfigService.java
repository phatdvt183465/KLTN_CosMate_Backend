package com.cosmate.service;

import com.cosmate.dto.request.system.SystemConfigUpdateRequest;
import com.cosmate.entity.SystemConfig;

import java.util.List;

public interface SystemConfigService {
    List<SystemConfig> getAllConfigs();
    SystemConfig updateConfig(String configKey, SystemConfigUpdateRequest request);
}
