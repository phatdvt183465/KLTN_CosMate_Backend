package com.cosmate.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminMenuPermissionResponse {
    private UUID id;
    private String name;
    private String description;
    private Integer displayOrder;
    private Boolean isActive;
    private List<String> visibleForRoles;
    private List<MenuItemPermissionResponse> menuItems;
}
