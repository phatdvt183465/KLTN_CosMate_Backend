package com.cosmate.controller;

import com.cosmate.dto.request.MenuItemRequest;
import com.cosmate.dto.response.MenuItemResponse;
import com.cosmate.service.MenuItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/menu-items")
@RequiredArgsConstructor
public class MenuItemController {

    private final MenuItemService menuItemService;

    @GetMapping
    public ResponseEntity<Page<MenuItemResponse>> getAllMenuItems(Pageable pageable) {
        return ResponseEntity.ok(menuItemService.getAllMenuItems(pageable));
    }

    @GetMapping("/menu/{menuId}")
    public ResponseEntity<List<MenuItemResponse>> getMenuItemsByMenu(@PathVariable UUID menuId) {
        return ResponseEntity.ok(menuItemService.getMenuItemsByMenu(menuId));
    }

    @GetMapping("/menu/{menuId}/root")
    public ResponseEntity<List<MenuItemResponse>> getRootMenuItems(@PathVariable UUID menuId) {
        return ResponseEntity.ok(menuItemService.getRootMenuItems(menuId));
    }

    @GetMapping("/parent/{parentId}")
    public ResponseEntity<List<MenuItemResponse>> getChildMenuItems(@PathVariable UUID parentId) {
        return ResponseEntity.ok(menuItemService.getChildMenuItems(parentId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MenuItemResponse> getMenuItemById(@PathVariable UUID id) {
        return ResponseEntity.ok(menuItemService.getMenuItemById(id));
    }

    @PostMapping
    public ResponseEntity<MenuItemResponse> createMenuItem(@RequestBody MenuItemRequest menuItemRequest) {
        return new ResponseEntity<>(menuItemService.createMenuItem(menuItemRequest), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MenuItemResponse> updateMenuItem(@PathVariable UUID id, @RequestBody MenuItemRequest menuItemRequest) {
        return ResponseEntity.ok(menuItemService.updateMenuItem(id, menuItemRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMenuItem(@PathVariable UUID id) {
        menuItemService.deleteMenuItem(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<MenuItemResponse> toggleMenuItemStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(menuItemService.toggleMenuItemStatus(id));
    }
}