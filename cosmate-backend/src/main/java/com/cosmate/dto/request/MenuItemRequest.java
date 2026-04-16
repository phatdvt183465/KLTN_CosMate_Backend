package com.cosmate.dto.request;

import lombok.Data;
import java.util.UUID;

@Data
public class MenuItemRequest {
    private UUID menuId;
    private UUID parentId;
    private String title;
    private String url;
    private String icon;
    private String description;
    private Integer displayOrder;
    private String requiredPermission;
}