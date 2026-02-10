package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Order_Addresses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderAddress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "order_id")
    private Integer orderId;

    @Column(name = "address_from", length = 255)
    private String addressFrom;

    @Column(length = 255)
    private String name;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String district;

    @Column(length = 255)
    private String address;

    @Column(length = 20)
    private String phone;
}

