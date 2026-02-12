package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;

@Entity
@Table(name = "Order_Rental_Option")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRentalOption {
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
    @Column(name = "option_name", length = 100)
    private String optionName;

    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    @Nationalized
    @Column(name = "description", length = 500)
    private String description;
}
