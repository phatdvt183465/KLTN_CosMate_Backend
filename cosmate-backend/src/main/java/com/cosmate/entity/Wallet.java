package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "Wallets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wallet_id")
    private Integer walletId;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "balance", precision = 19, scale = 4, nullable = false)
    private BigDecimal balance;

    @Column(name = "deposit_balance", precision = 19, scale = 4, nullable = false)
    private BigDecimal depositBalance;

    // Keep balances non-null; initialize defaults if missing before persist
    @PrePersist
    public void prePersist() {
        if (balance == null) balance = BigDecimal.ZERO;
        if (depositBalance == null) depositBalance = BigDecimal.ZERO;
    }
}
