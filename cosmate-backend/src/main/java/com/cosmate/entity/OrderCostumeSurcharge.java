package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "Order_Costumes_Surcharges")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCostumeSurcharge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    @Column(name = "costume_id", nullable = false)
    private Integer costumeId;

    @Column(length = 255, nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal price;
}

