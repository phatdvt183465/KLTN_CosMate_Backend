package com.example.starter_project_2025.system.menu.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class MenuDTO {
    private UUID id;

    @NotBlank(message = "Menu name is required")
    private String name;

    private String description;
    private Boolean isActive = true;
    private Integer displayOrder = 0;
    private List<MenuItemDTO> menuItems = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
