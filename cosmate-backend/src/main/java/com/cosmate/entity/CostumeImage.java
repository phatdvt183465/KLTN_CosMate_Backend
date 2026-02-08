package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "Costume_Images")
@Data
public class CostumeImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "costume_id")
    private Costume costume;

    @Column(name = "image_url")
    private String imageUrl;

    private String type;
}