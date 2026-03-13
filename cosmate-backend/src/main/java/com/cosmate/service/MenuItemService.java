package com.cosmate.service;

import com.cosmate.dto.request.MenuItemRequest;
import com.cosmate.dto.response.MenuItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface MenuItemService {
    Page<MenuItemResponse> getAllMenuItems(Pageable pageable);
    List<MenuItemResponse> getMenuItemsByMenu(UUID menuId);
    List<MenuItemResponse> getRootMenuItems(UUID menuId);
    List<MenuItemResponse> getChildMenuItems(UUID parentId);
    MenuItemResponse getMenuItemById(UUID id);
    MenuItemResponse createMenuItem(MenuItemRequest request);
    MenuItemResponse updateMenuItem(UUID id, MenuItemRequest request);
    void deleteMenuItem(UUID id);
    MenuItemResponse toggleMenuItemStatus(UUID id);
}