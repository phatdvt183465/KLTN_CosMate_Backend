package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "cosplayer_id")
    private Integer cosplayerId;

    @Column(name = "provider_id")
    private Integer providerId;

    @Column(name = "order_type", length = 50)
    private String orderType;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "total_amount", precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "total_deposit_amount", precision = 12, scale = 2)
    private BigDecimal totalDepositAmount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
