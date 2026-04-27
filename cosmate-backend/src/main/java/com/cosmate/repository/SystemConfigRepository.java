package com.cosmate.repository;

import com.cosmate.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, String> {
    boolean existsByConfigKey(String configKey);
}
