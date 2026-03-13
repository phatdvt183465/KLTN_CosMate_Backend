package com.example.starter_project_2025.system.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class MenuItemDTO {
    private UUID id;

    @NotNull(message = "Menu ID is required")
    private UUID menuId;

    private UUID parentId;

    @NotBlank(message = "Title is required")
    private String title;

    private String url;
    private String icon;
    private String description;
    private Integer displayOrder = 0;
    private Boolean isActive = true;
    private String requiredPermission;
    private List<MenuItemDTO> children = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
