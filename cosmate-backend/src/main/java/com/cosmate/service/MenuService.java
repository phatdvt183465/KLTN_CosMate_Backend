package com.cosmate.service;

import com.cosmate.dto.request.MenuRequest;
import com.cosmate.dto.response.MenuResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface MenuService {
    Page<MenuResponse> getAllMenus(Pageable pageable);
    List<MenuResponse> getAllMenusList();
    List<MenuResponse> getActiveMenus();
    MenuResponse getMenuById(UUID id);
    MenuResponse createMenu(MenuRequest request);
    MenuResponse updateMenu(UUID id, MenuRequest request);
    void deleteMenu(UUID id);
    MenuResponse toggleMenuStatus(UUID id);
}