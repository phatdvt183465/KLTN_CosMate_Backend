package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "Order_Detail_Images")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "order_detail_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private OrderDetail orderDetail;

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
