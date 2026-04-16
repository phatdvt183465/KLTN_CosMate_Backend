package com.cosmate.service.impl;

import com.cosmate.dto.request.MenuRequest;
import com.cosmate.dto.response.MenuItemResponse;
import com.cosmate.dto.response.MenuResponse;
import com.cosmate.entity.Menu;
import com.cosmate.entity.MenuItem;
import com.cosmate.exception.BadRequestException;
import com.cosmate.exception.ResourceNotFoundException;
import com.cosmate.repository.MenuRepository;
import com.cosmate.service.MenuService;
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
public class MenuServiceImpl implements MenuService {

    private final MenuRepository menuRepository;

    @Override
    @PreAuthorize("hasAuthority('MENU_READ')")
    public Page<MenuResponse> getAllMenus(Pageable pageable) {
        return menuRepository.findAll(pageable).map(this::convertToResponse);
    }

    @Override
    @PreAuthorize("hasAuthority('MENU_READ')")
    public List<MenuResponse> getAllMenusList() {
        return menuRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<MenuResponse> getActiveMenus() {
        return menuRepository.findActiveMenusWithItems().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @PreAuthorize("hasAuthority('MENU_READ')")
    public MenuResponse getMenuById(UUID id) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu", "id", id));
        return convertToResponse(menu);
    }

    @Override
    @PreAuthorize("hasAuthority('MENU_CREATE')")
    public MenuResponse createMenu(MenuRequest request) {
        if (menuRepository.existsByName(request.getName())) {
            throw new BadRequestException("Menu name already exists: " + request.getName());
        }

        Menu menu = new Menu();
        menu.setName(request.getName());
        menu.setDescription(request.getDescription());
        menu.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);
        menu.setIsActive(true);

        Menu savedMenu = menuRepository.save(menu);
        log.info("Created new menu: {}", savedMenu.getName());
        return convertToResponse(savedMenu);
    }

    @Override
    @PreAuthorize("hasAuthority('MENU_UPDATE')")
    public MenuResponse updateMenu(UUID id, MenuRequest request) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu", "id", id));

        if (request.getName() != null && !request.getName().equals(menu.getName())) {
            if (menuRepository.existsByName(request.getName())) {
                throw new BadRequestException("Menu name already exists: " + request.getName());
            }
            menu.setName(request.getName());
        }

        if (request.getDescription() != null) {
            menu.setDescription(request.getDescription());
        }

        if (request.getDisplayOrder() != null) {
            menu.setDisplayOrder(request.getDisplayOrder());
        }

        Menu updatedMenu = menuRepository.save(menu);
        log.info("Updated menu: {}", updatedMenu.getName());
        return convertToResponse(updatedMenu);
    }

    @Override
    @PreAuthorize("hasAuthority('MENU_DELETE')")
    public void deleteMenu(UUID id) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu", "id", id));
        menuRepository.delete(menu);
        log.info("Deleted menu with id: {}", id);
    }

    @Override
    @PreAuthorize("hasAuthority('MENU_UPDATE')")
    public MenuResponse toggleMenuStatus(UUID id) {
        menuRepository.forceToggleStatus(id);
        Menu updatedMenu = menuRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu", "id", id));
        return convertToResponse(updatedMenu);
    }

    private MenuResponse convertToResponse(Menu menu) {
        MenuResponse response = new MenuResponse();
        response.setId(menu.getId());
        response.setName(menu.getName());
        response.setDescription(menu.getDescription());
        response.setDisplayOrder(menu.getDisplayOrder());
        response.setIsActive(menu.getIsActive());
        response.setVisibleForRoles(menu.getVisibleForRoles());
        response.setCreatedAt(menu.getCreatedAt());
        response.setUpdatedAt(menu.getUpdatedAt());

        if (menu.getMenuItems() != null) {
            response.setMenuItems(menu.getMenuItems().stream()
                    .map(this::convertMenuItemToResponse)
                    .collect(Collectors.toList()));
        }
        return response;
    }

    private MenuItemResponse convertMenuItemToResponse(MenuItem menuItem) {
        MenuItemResponse response = new MenuItemResponse();
        response.setId(menuItem.getId());
        response.setMenuId(menuItem.getMenu().getId());
        response.setParentId(menuItem.getParent() != null ? menuItem.getParent().getId() : null);
        response.setTitle(menuItem.getTitle());
        response.setUrl(menuItem.getUrl());
        response.setIcon(menuItem.getIcon());
        response.setDescription(menuItem.getDescription());
        response.setDisplayOrder(menuItem.getDisplayOrder());
        response.setIsActive(menuItem.getIsActive());
        response.setRequiredPermission(menuItem.getRequiredPermission());
        response.setVisibleForRoles(menuItem.getVisibleForRoles());
        response.setCreatedAt(menuItem.getCreatedAt());
        response.setUpdatedAt(menuItem.getUpdatedAt());

        if (menuItem.getChildren() != null && !menuItem.getChildren().isEmpty()) {
            response.setChildren(menuItem.getChildren().stream()
                    .map(this::convertMenuItemToResponse)
                    .collect(Collectors.toList()));
        }
        return response;
    }
}
