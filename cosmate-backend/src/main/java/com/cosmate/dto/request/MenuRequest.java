package com.cosmate.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class MenuRequest {
    private String name;
    private String description;
    private Integer displayOrder;
    private Boolean isActive;
    private List<String> visibleForRoles;
}
