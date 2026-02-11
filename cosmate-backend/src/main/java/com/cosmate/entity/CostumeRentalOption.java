package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Nationalized;
import java.math.BigDecimal;

@Entity
@Table(name = "Costume_Rental_Options")
@Data
public class CostumeRentalOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "costume_id")
    private Costume costume;

    @Nationalized
    private String name;

    private BigDecimal price;

    @Nationalized
    private String description;

    private String status;
}