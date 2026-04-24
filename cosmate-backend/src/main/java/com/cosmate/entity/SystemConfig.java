package com.cosmate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "System_Configs")
public class SystemConfig {
    @Id
    @Column(name = "Config_Key")
    private String configKey;

    @Column(name = "Config_Value", columnDefinition = "NVARCHAR(MAX)")
    private String configValue;

    private String description;
}