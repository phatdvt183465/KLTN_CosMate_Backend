package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity
@Table(name = "Providers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Provider {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Integer userId;

    @Column(name = "shop_name", length = 255)
    private String shopName;

    @Column(name = "shop_address_id")
    private Integer shopAddressId;

    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "bio", columnDefinition = "NVARCHAR(MAX)")
    private String bio;

    @Column(name = "bank_account_number", length = 100)
    private String bankAccountNumber;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "completed_orders", nullable = false)
    private Integer completedOrders = 0;

    @Column(name = "total_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal totalRating = BigDecimal.ZERO;

    @Column(name = "total_reviews", nullable = false)
    private Integer totalReviews = 0;

    @Column(name = "verified", nullable = false)
    private Boolean verified = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (verified == null) verified = false;
        if (completedOrders == null) completedOrders = 0;
        if (totalRating == null) totalRating = BigDecimal.ZERO;
        if (totalReviews == null) totalReviews = 0;
    }
}
