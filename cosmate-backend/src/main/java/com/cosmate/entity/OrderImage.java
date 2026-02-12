package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "Orders_Image")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "order_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Order order;

    @Nationalized
    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(length = 50)
    private String stage;

    @Nationalized
    @Column(length = 255)
    private String note;

    private Boolean confirm;
}
