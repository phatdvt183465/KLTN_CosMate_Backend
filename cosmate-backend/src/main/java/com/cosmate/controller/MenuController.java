package com.cosmate.controller;

import com.cosmate.dto.request.MenuRequest;
import com.cosmate.dto.response.MenuResponse;
import com.cosmate.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping
    public ResponseEntity<Page<MenuResponse>> getAllMenus(Pageable pageable) {
        return ResponseEntity.ok(menuService.getAllMenus(pageable));
    }

    @GetMapping("/list")
    public ResponseEntity<List<MenuResponse>> getAllMenusList() {
        return ResponseEntity.ok(menuService.getAllMenusList());
    }

    @GetMapping("/active")
    public ResponseEntity<List<MenuResponse>> getActiveMenus() {
        return ResponseEntity.ok(menuService.getActiveMenus());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MenuResponse> getMenuById(@PathVariable UUID id) {
        return ResponseEntity.ok(menuService.getMenuById(id));
    }

    @PostMapping
    public ResponseEntity<MenuResponse> createMenu(@RequestBody MenuRequest menuRequest) {
        return new ResponseEntity<>(menuService.createMenu(menuRequest), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MenuResponse> updateMenu(@PathVariable UUID id, @RequestBody MenuRequest menuRequest) {
        return ResponseEntity.ok(menuService.updateMenu(id, menuRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMenu(@PathVariable UUID id) {
        menuService.deleteMenu(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<MenuResponse> toggleMenuStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(menuService.toggleMenuStatus(id));
    }
}