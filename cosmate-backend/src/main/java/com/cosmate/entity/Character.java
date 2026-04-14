package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Characters")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Character {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Nationalized
    @Column(length = 255)
    private String name;

    @Nationalized
    @Column(length = 255)
    private String anime;

    private Byte gender;

    @Nationalized
    @Column(name = "eye_color", length = 50)
    private String eyeColor;

    @Nationalized
    @Column(name = "hair_color", length = 50)
    private String hairColor;

    @Nationalized
    @Column(name = "hair_length", length = 50)
    private String hairLength;

    @Nationalized
    @Column(length = 20)
    private String age;

    @Nationalized
    @Column(length = 20)
    private String ear;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @ManyToMany(mappedBy = "characters")
    @Builder.Default
    private List<Costume> costumes = new ArrayList<>();
}
