package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Token_Purchase_History")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenPurchaseHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "subscription_id", nullable = false)
    private AiTokenSubscriptionPlan subscriptionPlan;

    @ManyToOne
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @Column(name = "price_at_purchase", precision = 18, scale = 2)
    private BigDecimal priceAtPurchase;

    @Column(name = "tokens_added")
    private Integer tokensAdded;

    @Column(name = "purchase_date")
    private LocalDateTime purchaseDate;

    @Column(name = "status", length = 50)
    private String status; // Success, Pending, Failed

    @PrePersist
    public void prePersist() {
        if (purchaseDate == null) purchaseDate = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }
}

