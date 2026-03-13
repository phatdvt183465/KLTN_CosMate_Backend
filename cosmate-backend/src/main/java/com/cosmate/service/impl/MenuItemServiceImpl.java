package com.cosmate.service.impl;

import com.cosmate.dto.request.MenuItemRequest;
import com.cosmate.dto.response.MenuItemResponse;
import com.cosmate.entity.Menu;
import com.cosmate.entity.MenuItem;
import com.cosmate.exception.BadRequestException;
import com.cosmate.exception.ResourceNotFoundException;
import com.cosmate.repository.MenuItemRepository;
import com.cosmate.repository.MenuRepository;
import com.cosmate.service.MenuItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MenuItemServiceImpl implements MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final MenuRepository menuRepository;

    @Override
    @PreAuthorize("hasAuthority('MENU_ITEM_READ')")
    public Page<MenuItemResponse> getAllMenuItems(Pageable pageable) {
        return menuItemRepository.findAll(pageable).map(this::convertToResponse);
    }

    @Override
    @PreAuthorize("hasAuthority('MENU_ITEM_READ')")
    public List<MenuItemResponse> getMenuItemsByMenu(UUID menuId) {
        return menuItemRepository.findByMenuIdOrderByDisplayOrderAsc(menuId).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @PreAuthorize("hasAuthority('MENU_ITEM_READ')")
    public List<MenuItemResponse> getRootMenuItems(UUID menuId) {
        return menuItemRepository.findRootItemsByMenuId(menuId).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @PreAuthorize("hasAuthority('MENU_ITEM_READ')")
    public List<MenuItemResponse> getChildMenuItems(UUID parentId) {
        return menuItemRepository.findByParentIdOrderByDisplayOrderAsc(parentId).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @PreAuthorize("hasAuthority('MENU_ITEM_READ')")
    public MenuItemResponse getMenuItemById(UUID id) {
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", id));
        return convertToResponse(menuItem);
    }

    @Override
    @PreAuthorize("hasAuthority('MENU_ITEM_CREATE')")
    public MenuItemResponse createMenuItem(MenuItemRequest request) {
        Menu menu = menuRepository.findById(request.getMenuId())
                .orElseThrow(() -> new ResourceNotFoundException("Menu", "id", request.getMenuId()));

        MenuItem menuItem = new MenuItem();
        menuItem.setMenu(menu);
        menuItem.setTitle(request.getTitle());
        menuItem.setUrl(request.getUrl());
        menuItem.setIcon(request.getIcon());
        menuItem.setDescription(request.getDescription());
        menuItem.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);
        menuItem.setIsActive(true);

        if (request.getRequiredPermission() != null) {
            menuItem.setRequiredPermission(request.getRequiredPermission());
        }

        if (request.getParentId() != null) {
            MenuItem parent = menuItemRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", request.getParentId()));
            menuItem.setParent(parent);
        }

        MenuItem savedMenuItem = menuItemRepository.save(menuItem);
        log.info("Created new menu item: {}", savedMenuItem.getTitle());
        return convertToResponse(savedMenuItem);
    }

    @Override
    @PreAuthorize("hasAuthority('MENU_ITEM_UPDATE')")
    public MenuItemResponse updateMenuItem(UUID id, MenuItemRequest request) {
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", id));

        if (request.getTitle() != null) {
            menuItem.setTitle(request.getTitle());
        }
        if (request.getUrl() != null) {
            menuItem.setUrl(request.getUrl());
        }
        if (request.getIcon() != null) {
            menuItem.setIcon(request.getIcon());
        }
        if (request.getDescription() != null) {
            menuItem.setDescription(request.getDescription());
        }
        if (request.getDisplayOrder() != null) {
            menuItem.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getRequiredPermission() != null) {
            menuItem.setRequiredPermission(request.getRequiredPermission());
        }
        if (request.getParentId() != null) {
            if (request.getParentId().equals(id)) {
                throw new BadRequestException("MenuItem cannot be its own parent");
            }
            MenuItem parent = menuItemRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", request.getParentId()));
            menuItem.setParent(parent);
        }

        MenuItem updatedMenuItem = menuItemRepository.save(menuItem);
        log.info("Updated menu item: {}", updatedMenuItem.getTitle());
        return convertToResponse(updatedMenuItem);
    }

    @Override
    @PreAuthorize("hasAuthority('MENU_ITEM_DELETE')")
    public void deleteMenuItem(UUID id) {
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", id));
        menuItemRepository.delete(menuItem);
        log.info("Deleted menu item with id: {}", id);
    }

    @Override
    @PreAuthorize("hasAuthority('MENU_ITEM_UPDATE')")
    public MenuItemResponse toggleMenuItemStatus(UUID id) {
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", id));
        menuItem.setIsActive(!menuItem.getIsActive());
        MenuItem updatedMenuItem = menuItemRepository.save(menuItem);
        return convertToResponse(updatedMenuItem);
    }

    private MenuItemResponse convertToResponse(MenuItem menuItem) {
        MenuItemResponse response = new MenuItemResponse();
        response.setId(menuItem.getId());
        response.setTitle(menuItem.getTitle());
        response.setUrl(menuItem.getUrl());
        response.setIcon(menuItem.getIcon());
        response.setDescription(menuItem.getDescription());
        response.setDisplayOrder(menuItem.getDisplayOrder());
        response.setIsActive(menuItem.getIsActive());
        response.setRequiredPermission(menuItem.getRequiredPermission());
        response.setCreatedAt(menuItem.getCreatedAt());
        response.setUpdatedAt(menuItem.getUpdatedAt());

        if (menuItem.getMenu() != null) {
            response.setMenuId(menuItem.getMenu().getId());
        }

        if (menuItem.getParent() != null) {
            response.setParentId(menuItem.getParent().getId());
        }

        return response;
    }
}