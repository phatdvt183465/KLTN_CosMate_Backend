package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "Character_Requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CharacterRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "character_name", length = 255, nullable = false)
    private String characterName;

    @Column(name = "anime_name", length = 255, nullable = false)
    private String animeName;

    @Column(name = "provider_id", nullable = false)
    private Integer providerId;

    @Column(name = "status", length = 20, nullable = false)
    private String status; // PENDING, APPROVED, REJECTED

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null || status.isBlank()) status = "PENDING";
    }
}
