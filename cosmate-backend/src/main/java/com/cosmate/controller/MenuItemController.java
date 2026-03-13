package com.example.starter_project_2025.system.menu.controller;

import com.example.starter_project_2025.system.menu.dto.MenuItemDTO;
import com.example.starter_project_2025.system.menu.service.MenuItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/menu-items")
@RequiredArgsConstructor
@Tag(name = "Menu Item Management", description = "APIs for managing menu items")
@SecurityRequirement(name = "Bearer Authentication")
public class MenuItemController {

    private final MenuItemService menuItemService;

    @GetMapping
    @Operation(summary = "Get all menu items", description = "Retrieve all menu items with pagination")
    public ResponseEntity<Page<MenuItemDTO>> getAllMenuItems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "displayOrder,asc") String[] sort) {

        Sort.Direction direction = Sort.Direction.fromString(sort[1]);
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort[0]));
        Page<MenuItemDTO> menuItems = menuItemService.getAllMenuItems(pageable);
        return ResponseEntity.ok(menuItems);
    }

    @GetMapping("/menu/{menuId}")
    @Operation(summary = "Get menu items by menu", description = "Retrieve all menu items for a specific menu")
    public ResponseEntity<List<MenuItemDTO>> getMenuItemsByMenu(@PathVariable UUID menuId) {
        List<MenuItemDTO> menuItems = menuItemService.getMenuItemsByMenu(menuId);
        return ResponseEntity.ok(menuItems);
    }

    @GetMapping("/menu/{menuId}/root")
    @Operation(summary = "Get root menu items", description = "Retrieve root-level menu items for a specific menu")
    public ResponseEntity<List<MenuItemDTO>> getRootMenuItems(@PathVariable UUID menuId) {
        List<MenuItemDTO> menuItems = menuItemService.getRootMenuItems(menuId);
        return ResponseEntity.ok(menuItems);
    }

    @GetMapping("/parent/{parentId}")
    @Operation(summary = "Get child menu items", description = "Retrieve child menu items for a specific parent")
    public ResponseEntity<List<MenuItemDTO>> getChildMenuItems(@PathVariable UUID parentId) {
        List<MenuItemDTO> menuItems = menuItemService.getChildMenuItems(parentId);
        return ResponseEntity.ok(menuItems);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get menu item by ID", description = "Retrieve a specific menu item by ID")
    public ResponseEntity<MenuItemDTO> getMenuItemById(@PathVariable UUID id) {
        MenuItemDTO menuItem = menuItemService.getMenuItemById(id);
        return ResponseEntity.ok(menuItem);
    }

    @PostMapping
    @Operation(summary = "Create menu item", description = "Create a new menu item")
    public ResponseEntity<MenuItemDTO> createMenuItem(@Valid @RequestBody MenuItemDTO menuItemDTO) {
        MenuItemDTO createdMenuItem = menuItemService.createMenuItem(menuItemDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdMenuItem);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update menu item", description = "Update an existing menu item")
    public ResponseEntity<MenuItemDTO> updateMenuItem(
            @PathVariable UUID id,
            @Valid @RequestBody MenuItemDTO menuItemDTO) {
        MenuItemDTO updatedMenuItem = menuItemService.updateMenuItem(id, menuItemDTO);
        return ResponseEntity.ok(updatedMenuItem);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete menu item", description = "Delete a menu item by ID")
    public ResponseEntity<Void> deleteMenuItem(@PathVariable UUID id) {
        menuItemService.deleteMenuItem(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/toggle-status")
    @Operation(summary = "Toggle menu item locationStatus", description = "Activate or deactivate a menu item")
    public ResponseEntity<MenuItemDTO> toggleMenuItemStatus(@PathVariable UUID id) {
        MenuItemDTO menuItem = menuItemService.toggleMenuItemStatus(id);
        return ResponseEntity.ok(menuItem);
    }
}
