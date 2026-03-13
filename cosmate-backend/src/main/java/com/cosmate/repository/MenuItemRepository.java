package com.example.starter_project_2025.system.menu.repository;

import com.example.starter_project_2025.system.menu.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {
    List<MenuItem> findByMenuIdOrderByDisplayOrderAsc(UUID menuId);
    List<MenuItem> findByParentIdOrderByDisplayOrderAsc(UUID parentId);
    List<MenuItem> findByIsActive(Boolean isActive);

    @Query("SELECT mi FROM MenuItem mi WHERE mi.menu.id = :menuId AND mi.parent IS NULL ORDER BY mi.displayOrder")
    List<MenuItem> findRootItemsByMenuId(@Param("menuId") UUID menuId);
}
