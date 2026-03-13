package com.example.starter_project_2025.system.menu.service;

import com.example.starter_project_2025.system.menu.dto.MenuItemDTO;
import com.example.starter_project_2025.system.menu.entity.Menu;
import com.example.starter_project_2025.system.menu.entity.MenuItem;
import com.example.starter_project_2025.exception.BadRequestException;
import com.example.starter_project_2025.exception.ResourceNotFoundException;
import com.example.starter_project_2025.system.menu.repository.MenuItemRepository;
import com.example.starter_project_2025.system.menu.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
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
@Transactional
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final MenuRepository menuRepository;

    @PreAuthorize("hasAuthority('MENU_ITEM_READ')")
    public Page<MenuItemDTO> getAllMenuItems(Pageable pageable) {
        return menuItemRepository.findAll(pageable).map(this::convertToDTO);
    }

    @PreAuthorize("hasAuthority('MENU_ITEM_READ')")
    public List<MenuItemDTO> getMenuItemsByMenu(UUID menuId) {
        return menuItemRepository.findByMenuIdOrderByDisplayOrderAsc(menuId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasAuthority('MENU_ITEM_READ')")
    public List<MenuItemDTO> getRootMenuItems(UUID menuId) {
        return menuItemRepository.findRootItemsByMenuId(menuId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasAuthority('MENU_ITEM_READ')")
    public List<MenuItemDTO> getChildMenuItems(UUID parentId) {
        return menuItemRepository.findByParentIdOrderByDisplayOrderAsc(parentId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasAuthority('MENU_ITEM_READ')")
    public MenuItemDTO getMenuItemById(UUID id) {
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", id));
        return convertToDTO(menuItem);
    }

    @PreAuthorize("hasAuthority('MENU_ITEM_CREATE')")
    public MenuItemDTO createMenuItem(MenuItemDTO menuItemDTO) {
        Menu menu = menuRepository.findById(menuItemDTO.getMenuId())
                .orElseThrow(() -> new ResourceNotFoundException("Menu", "id", menuItemDTO.getMenuId()));

        MenuItem menuItem = new MenuItem();
        menuItem.setMenu(menu);
        menuItem.setTitle(menuItemDTO.getTitle());
        menuItem.setUrl(menuItemDTO.getUrl());
        menuItem.setIcon(menuItemDTO.getIcon());
        menuItem.setDescription(menuItemDTO.getDescription());
        menuItem.setDisplayOrder(menuItemDTO.getDisplayOrder() != null ? menuItemDTO.getDisplayOrder() : 0);
        menuItem.setIsActive(true);

        if (menuItemDTO.getRequiredPermission() != null) {
            menuItem.setRequiredPermission(menuItemDTO.getRequiredPermission());
        }

        if (menuItemDTO.getParentId() != null) {
            MenuItem parent = menuItemRepository.findById(menuItemDTO.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", menuItemDTO.getParentId()));
            menuItem.setParent(parent);
        }

        MenuItem savedMenuItem = menuItemRepository.save(menuItem);
        return convertToDTO(savedMenuItem);
    }

    @PreAuthorize("hasAuthority('MENU_ITEM_UPDATE')")
    public MenuItemDTO updateMenuItem(UUID id, MenuItemDTO menuItemDTO) {
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", id));

        if (menuItemDTO.getTitle() != null) {
            menuItem.setTitle(menuItemDTO.getTitle());
        }

        if (menuItemDTO.getUrl() != null) {
            menuItem.setUrl(menuItemDTO.getUrl());
        }

        if (menuItemDTO.getIcon() != null) {
            menuItem.setIcon(menuItemDTO.getIcon());
        }

        if (menuItemDTO.getDescription() != null) {
            menuItem.setDescription(menuItemDTO.getDescription());
        }

        if (menuItemDTO.getDisplayOrder() != null) {
            menuItem.setDisplayOrder(menuItemDTO.getDisplayOrder());
        }

        if (menuItemDTO.getRequiredPermission() != null) {
            menuItem.setRequiredPermission(menuItemDTO.getRequiredPermission());
        }

        if (menuItemDTO.getParentId() != null) {
            if (menuItemDTO.getParentId().equals(id)) {
                throw new BadRequestException("MenuItem cannot be its own parent");
            }
            MenuItem parent = menuItemRepository.findById(menuItemDTO.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", menuItemDTO.getParentId()));
            menuItem.setParent(parent);
        }

        MenuItem updatedMenuItem = menuItemRepository.save(menuItem);
        return convertToDTO(updatedMenuItem);
    }

    @PreAuthorize("hasAuthority('MENU_ITEM_DELETE')")
    public void deleteMenuItem(UUID id) {
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", id));
        menuItemRepository.delete(menuItem);
    }

    @PreAuthorize("hasAuthority('MENU_ITEM_UPDATE')")
    public MenuItemDTO toggleMenuItemStatus(UUID id) {
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", id));
        menuItem.setIsActive(!menuItem.getIsActive());
        MenuItem updatedMenuItem = menuItemRepository.save(menuItem);
        return convertToDTO(updatedMenuItem);
    }

    private MenuItemDTO convertToDTO(MenuItem menuItem) {
        MenuItemDTO dto = new MenuItemDTO();
        dto.setId(menuItem.getId());
        dto.setTitle(menuItem.getTitle());
        dto.setUrl(menuItem.getUrl());
        dto.setIcon(menuItem.getIcon());
        dto.setDescription(menuItem.getDescription());
        dto.setDisplayOrder(menuItem.getDisplayOrder());
        dto.setIsActive(menuItem.getIsActive());
        dto.setRequiredPermission(menuItem.getRequiredPermission());
        dto.setCreatedAt(menuItem.getCreatedAt());
        dto.setUpdatedAt(menuItem.getUpdatedAt());

        if (menuItem.getMenu() != null) {
            dto.setMenuId(menuItem.getMenu().getId());
        }

        if (menuItem.getParent() != null) {
            dto.setParentId(menuItem.getParent().getId());
        }

        return dto;
    }
}
