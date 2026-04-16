package com.cosmate.dto.request;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class MenuItemRequest {
    private UUID menuId;
    private UUID parentId;
    private String title;
    private String description;
    private String url;
    private String icon;
    private Integer displayOrder;
    private Boolean isActive;
    private String requiredPermission;
    private List<String> visibleForRoles;
}
