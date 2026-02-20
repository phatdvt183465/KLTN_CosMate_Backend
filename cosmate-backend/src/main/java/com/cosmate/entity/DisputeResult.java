package com.cosmate.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Disputes_Result")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne
    @JoinColumn(name = "dispute_id")
    @JsonBackReference
    private Dispute dispute;

    @Column(length = 255)
    private String result; // e.g. AWARD_COSPLAYER, AWARD_PROVIDER, SPLIT, NO_ACTION

    @Column(name = "penalty_amount", precision = 12, scale = 2)
    private BigDecimal penaltyAmount;

    @Column(name = "penalty_percent", precision = 5, scale = 2)
    private BigDecimal penaltyPercent;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
