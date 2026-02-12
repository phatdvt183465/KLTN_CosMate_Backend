package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;

@Entity
@Table(name = "Order_Detail_Accessories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDetailAccessory {
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
    @Column(name = "accessory_name", length = 255)
    private String accessoryName;

    @Nationalized
    @Column(name = "accessory_description", length = 500)
    private String accessoryDescription;

    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;
}
