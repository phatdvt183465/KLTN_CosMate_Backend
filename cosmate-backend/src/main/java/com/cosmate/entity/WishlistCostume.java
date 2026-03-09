package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "Wishlist_Costumes", uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id", "costume_id"})})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WishlistCostume {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "costume_id", nullable = false)
    private Integer costumeId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

