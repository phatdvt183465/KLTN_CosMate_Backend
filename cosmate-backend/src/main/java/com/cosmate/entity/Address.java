package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Users_Addresses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
