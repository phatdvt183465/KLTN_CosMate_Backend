package com.cosmate.repository;

import com.cosmate.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

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
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Menu m SET m.isActive = CASE WHEN m.isActive = true THEN false ELSE true END WHERE m.id = :id")
    void forceToggleStatus(@Param("id") UUID id);
}
