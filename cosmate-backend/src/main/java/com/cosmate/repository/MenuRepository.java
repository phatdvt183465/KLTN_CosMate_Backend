package com.example.starter_project_2025.system.menu.repository;

import com.example.starter_project_2025.system.menu.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuRepository extends JpaRepository<Menu, UUID> {
    Optional<Menu> findByName(String name);

    @Query("SELECT DISTINCT m FROM Menu m LEFT JOIN FETCH m.menuItems WHERE m.isActive = true ORDER BY m.displayOrder ASC")
    List<Menu> findActiveMenusWithItems();

    List<Menu> findByIsActive(Boolean isActive);
    List<Menu> findAllByOrderByDisplayOrderAsc();
    boolean existsByName(String name);
}
