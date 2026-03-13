package com.example.starter_project_2025.system.menu.service;

import com.example.starter_project_2025.system.menu.dto.MenuDTO;
import com.example.starter_project_2025.system.menu.dto.MenuItemDTO;
import com.example.starter_project_2025.system.menu.entity.Menu;
import com.example.starter_project_2025.exception.BadRequestException;
import com.example.starter_project_2025.exception.ResourceNotFoundException;
import com.example.starter_project_2025.system.menu.repository.MenuRepository;
import com.example.starter_project_2025.system.menu.entity.MenuItem;
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
public class MenuService {

    private final MenuRepository menuRepository;

    @PreAuthorize("hasAuthority('MENU_READ')")
    public Page<MenuDTO> getAllMenus(Pageable pageable) {
        return menuRepository.findAll(pageable).map(this::convertToDTO);
    }

    @PreAuthorize("hasAuthority('MENU_READ')")
    public List<MenuDTO> getAllMenusList() {
        return menuRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<MenuDTO> getActiveMenus() {
        return menuRepository.findActiveMenusWithItems().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasAuthority('MENU_READ')")
    public MenuDTO getMenuById(UUID id) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu", "id", id));
        return convertToDTO(menu);
    }

    @PreAuthorize("hasAuthority('MENU_CREATE')")
    public MenuDTO createMenu(MenuDTO menuDTO) {
        if (menuRepository.existsByName(menuDTO.getName())) {
            throw new BadRequestException("Menu name already exists: " + menuDTO.getName());
        }

        Menu menu = new Menu();
        menu.setName(menuDTO.getName());
        menu.setDescription(menuDTO.getDescription());
        menu.setDisplayOrder(menuDTO.getDisplayOrder() != null ? menuDTO.getDisplayOrder() : 0);
        menu.setIsActive(true);

        Menu savedMenu = menuRepository.save(menu);
        return convertToDTO(savedMenu);
    }

    @PreAuthorize("hasAuthority('MENU_UPDATE')")
    public MenuDTO updateMenu(UUID id, MenuDTO menuDTO) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu", "id", id));

        if (menuDTO.getName() != null && !menuDTO.getName().equals(menu.getName())) {
            if (menuRepository.existsByName(menuDTO.getName())) {
                throw new BadRequestException("Menu name already exists: " + menuDTO.getName());
            }
            menu.setName(menuDTO.getName());
        }

        if (menuDTO.getDescription() != null) {
            menu.setDescription(menuDTO.getDescription());
        }

        if (menuDTO.getDisplayOrder() != null) {
            menu.setDisplayOrder(menuDTO.getDisplayOrder());
        }

        Menu updatedMenu = menuRepository.save(menu);
        return convertToDTO(updatedMenu);
    }

    @PreAuthorize("hasAuthority('MENU_DELETE')")
    public void deleteMenu(UUID id) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu", "id", id));
        menuRepository.delete(menu);
    }

    @PreAuthorize("hasAuthority('MENU_UPDATE')")
    public MenuDTO toggleMenuStatus(UUID id) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu", "id", id));
        menu.setIsActive(!menu.getIsActive());
        Menu updatedMenu = menuRepository.save(menu);
        return convertToDTO(updatedMenu);
    }

    private MenuDTO convertToDTO(Menu menu) {
        MenuDTO dto = new MenuDTO();
        dto.setId(menu.getId());
        dto.setName(menu.getName());
        dto.setDescription(menu.getDescription());
        dto.setDisplayOrder(menu.getDisplayOrder());
        dto.setIsActive(menu.getIsActive());
        dto.setCreatedAt(menu.getCreatedAt());
        dto.setUpdatedAt(menu.getUpdatedAt());

        if (menu.getMenuItems() != null) {
            dto.setMenuItems(menu.getMenuItems().stream()
                .map(this::convertMenuItemToDTO)
                .collect(Collectors.toList()));
        }

        return dto;
    }

    private MenuItemDTO convertMenuItemToDTO(MenuItem menuItem) {
        MenuItemDTO dto = new MenuItemDTO();
        dto.setId(menuItem.getId());
        dto.setMenuId(menuItem.getMenu().getId());
        dto.setParentId(menuItem.getParent() != null ? menuItem.getParent().getId() : null);
        dto.setTitle(menuItem.getTitle());
        dto.setUrl(menuItem.getUrl());
        dto.setIcon(menuItem.getIcon());
        dto.setDisplayOrder(menuItem.getDisplayOrder());
        dto.setIsActive(menuItem.getIsActive());
        dto.setRequiredPermission(menuItem.getRequiredPermission());
        dto.setCreatedAt(menuItem.getCreatedAt());
        dto.setUpdatedAt(menuItem.getUpdatedAt());

        if (menuItem.getChildren() != null && !menuItem.getChildren().isEmpty()) {
            dto.setChildren(menuItem.getChildren().stream()
                .map(this::convertMenuItemToDTO)
                .collect(Collectors.toList()));
        }

        return dto;
    }
}
