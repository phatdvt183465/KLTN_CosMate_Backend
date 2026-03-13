package com.example.starter_project_2025.system.menu.controller;

import com.example.starter_project_2025.system.menu.dto.MenuDTO;
import com.example.starter_project_2025.system.menu.service.MenuService;
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
@RequestMapping("/api/menus")
@RequiredArgsConstructor
@Tag(name = "Menu Management", description = "APIs for managing menus")
@SecurityRequirement(name = "Bearer Authentication")
public class MenuController {

    private final MenuService menuService;

    @GetMapping
    @Operation(summary = "Get all menus", description = "Retrieve all menus with pagination")
    public ResponseEntity<Page<MenuDTO>> getAllMenus(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "displayOrder,asc") String[] sort) {

        Sort.Direction direction = Sort.Direction.fromString(sort[1]);
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort[0]));
        Page<MenuDTO> menus = menuService.getAllMenus(pageable);
        return ResponseEntity.ok(menus);
    }

    @GetMapping("/list")
    @Operation(summary = "Get all menus list", description = "Retrieve all menus as a list ordered by display order")
    public ResponseEntity<List<MenuDTO>> getAllMenusList() {
        List<MenuDTO> menus = menuService.getAllMenusList();
        return ResponseEntity.ok(menus);
    }

    @GetMapping("/active")
    @Operation(summary = "Get active menus", description = "Retrieve all active menus")
    public ResponseEntity<List<MenuDTO>> getActiveMenus() {
        List<MenuDTO> menus = menuService.getActiveMenus();
        return ResponseEntity.ok(menus);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get menu by ID", description = "Retrieve a specific menu by ID")
    public ResponseEntity<MenuDTO> getMenuById(@PathVariable UUID id) {
        MenuDTO menu = menuService.getMenuById(id);
        return ResponseEntity.ok(menu);
    }

    @PostMapping
    @Operation(summary = "Create menu", description = "Create a new menu")
    public ResponseEntity<MenuDTO> createMenu(@Valid @RequestBody MenuDTO menuDTO) {
        MenuDTO createdMenu = menuService.createMenu(menuDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdMenu);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update menu", description = "Update an existing menu")
    public ResponseEntity<MenuDTO> updateMenu(
            @PathVariable UUID id,
            @Valid @RequestBody MenuDTO menuDTO) {
        MenuDTO updatedMenu = menuService.updateMenu(id, menuDTO);
        return ResponseEntity.ok(updatedMenu);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete menu", description = "Delete a menu by ID")
    public ResponseEntity<Void> deleteMenu(@PathVariable UUID id) {
        menuService.deleteMenu(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/toggle-status")
    @Operation(summary = "Toggle menu locationStatus", description = "Activate or deactivate a menu")
    public ResponseEntity<MenuDTO> toggleMenuStatus(@PathVariable UUID id) {
        MenuDTO menu = menuService.toggleMenuStatus(id);
        return ResponseEntity.ok(menu);
    }
}
