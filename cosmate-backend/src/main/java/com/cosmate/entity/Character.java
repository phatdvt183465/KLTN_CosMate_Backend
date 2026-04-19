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

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToMany(mappedBy = "characters")
    @Builder.Default
    private List<Costume> costumes = new ArrayList<>();
}
