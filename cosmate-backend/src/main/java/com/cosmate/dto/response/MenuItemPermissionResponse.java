package com.cosmate.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemPermissionResponse {
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
    private List<String> visibleForRoles;
    private List<MenuItemPermissionResponse> children;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
