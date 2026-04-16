package com.cosmate.dto.response;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class MenuItemResponse {
    private UUID id;
    private UUID menuId;
    private UUID parentId;
    private String title;
    private String url;
    private String icon;
    private String description;
    private Integer displayOrder;
    private Boolean isActive;
    private String requiredPermission;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<MenuItemResponse> children;
}