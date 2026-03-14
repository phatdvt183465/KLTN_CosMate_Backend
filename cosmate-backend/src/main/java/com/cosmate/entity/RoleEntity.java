package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "[Roles]")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleEntity {
    @Id
    private Integer id;

    @Column(name = "role_name", length = 50, nullable = false, unique = true)
    private String roleName;
}

