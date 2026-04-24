package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Providers_Subcription")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "subscription_plan_id")
    private SubscriptionPlan subscriptionPlan;

    @ManyToOne
    @JoinColumn(name = "providers_id")
    private Provider provider;

    @Column(length = 100)
    private String name;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String duration;

    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(length = 50)
    private String status;

    @Column(name = "monthly_token", nullable = false, columnDefinition = "int default 0")
    private Integer monthlyToken = 0;

    @Column(name = "next_token_grant_at")
    private LocalDateTime nextTokenGrantAt;

    // transaction_id should be a foreign key to Transactions(id)
    @ManyToOne
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
