package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Order_Detail")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "order_id")
    private Integer orderId;

    @Column(name = "costume_id")
    private Integer costumeId;

    @Column(length = 50)
    private String size;

    @Column(name = "number_of_items")
    private Integer numberOfItems;

    @Column(name = "rent_day")
    private Integer rentDay;

    @Column(name = "rent_start")
    private LocalDateTime rentStart;

    @Column(name = "rent_end")
    private LocalDateTime rentEnd;

    @Column(name = "return_day")
    private LocalDateTime returnDay;

    @Column(name = "deposit_amount", precision = 12, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "rent_amount", precision = 12, scale = 2)
    private BigDecimal rentAmount;

    // sum of surcharges applied to this costume in the order
    @Column(name = "surcharge_amount", precision = 12, scale = 2)
    private BigDecimal surchargeAmount;

    @Column(name = "accessories_amount", precision = 12, scale = 2)
    private BigDecimal accessoriesAmount;

    @Column(name = "rent_option_amount", precision = 12, scale = 2)
    private BigDecimal rentOptionAmount;
}
