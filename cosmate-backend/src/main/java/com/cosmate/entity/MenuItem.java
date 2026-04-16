package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "menu_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private MenuItem parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private List<MenuItem> children = new ArrayList<>();

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(length = 255)
    private String url;

    @Column(length = 50)
    private String icon;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "required_permission", length = 100)
    private String requiredPermission;

    @ElementCollection
    @CollectionTable(name = "menu_item_visible_roles", joinColumns = @JoinColumn(name = "menu_item_id"))
    @Column(name = "role_name")
    private List<String> visibleForRoles = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
