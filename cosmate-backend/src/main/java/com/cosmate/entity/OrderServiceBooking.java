package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "Order_Services")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderServiceBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "order_id")
    private Integer orderId;

    @Column(name = "service_id")
    private Integer serviceId;

    @Column(name = "service_name", length = 255)
    private String serviceName;

    @Column(name = "booking_date")
    private LocalDate bookingDate;

    @Column(name = "time_slot", length = 100)
    private String timeSlot;

    @Column(name = "number_of_human")
    private Integer numberOfHuman;

    @Column(name = "deposit_slot_amount", precision = 12, scale = 2)
    private BigDecimal depositSlotAmount;

    @Column(name = "rent_slot_amount", precision = 12, scale = 2)
    private BigDecimal rentSlotAmount;
}
