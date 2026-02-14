package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "Reviews_Url")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewUrl {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "review_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Review review;

    @Nationalized
    @Column(name = "url", length = 255)
    private String url;
}

