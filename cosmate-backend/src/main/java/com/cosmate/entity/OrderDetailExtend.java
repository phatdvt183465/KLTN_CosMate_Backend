package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Order_Detail_Extend")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDetailExtend {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "order_detail_id", nullable = false)
    private Integer orderDetailId;

    @Column(name = "old_return_date", nullable = false)
    private LocalDateTime oldReturnDate;

    @Column(name = "new_return_date", nullable = false)
    private LocalDateTime newReturnDate;

    @Column(name = "extend_days", nullable = false)
    private Integer extendDays;

    @Column(name = "extend_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal extendPrice;

    @Column(name = "payment_status", length = 20, nullable = false)
    private String paymentStatus; // values: UNPAID, PAID, CANCELLED

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (paymentStatus == null) paymentStatus = "UNPAID";
    }
}

