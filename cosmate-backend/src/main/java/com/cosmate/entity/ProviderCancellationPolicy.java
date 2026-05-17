package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "Provider_Cancellation_Policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderCancellationPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(name = "min_hours_before", nullable = false)
    private Integer minHoursBefore;

    @Column(name = "max_hours_before")
    private Integer maxHoursBefore;

    // NONE | PERCENT | FIXED
    @Column(name = "penalty_type", length = 20, nullable = false)
    private String penaltyType;

    @Column(name = "penalty_value", precision = 10, scale = 2, nullable = false)
    private java.math.BigDecimal penaltyValue;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (penaltyValue == null) penaltyValue = java.math.BigDecimal.ZERO;
    }
}

