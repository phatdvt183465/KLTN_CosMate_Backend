package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "Service_Albums")
@Data
public class ServiceAlbum {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "service_id")
    private Service service;

    @Column(name = "image_url")
    private String imageUrl;
}