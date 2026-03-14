package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "Users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 100, name = "username")
    private String username;

    // map to password_hash column in DB
    @Column(length = 255, name = "password_hash")
    private String passwordHash;

    @Column(length = 255, unique = true, name = "email")
    private String email;

    // map to full_name column in DB
    @Column(length = 255, name = "full_name")
    private String fullName;

    // map to avatar_url column in DB
    @Column(length = 255, name = "avatar_url")
    private String avatarUrl;

    @Column(length = 20, name = "phone")
    private String phone;

    @Column(length = 20, name = "status")
    private String status;

    // map to created_at column in DB
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Replace previous ElementCollection of enum roles with a ManyToOne relation to the roles table
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private RoleEntity role;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = "ACTIVE";
    }
}
