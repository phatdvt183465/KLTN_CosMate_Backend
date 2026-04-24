package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "AI_Token_Subcription_Plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiTokenSubscriptionPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "price", precision = 18, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(name = "number_of_token", nullable = false)
    private Integer numberOfToken;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}

