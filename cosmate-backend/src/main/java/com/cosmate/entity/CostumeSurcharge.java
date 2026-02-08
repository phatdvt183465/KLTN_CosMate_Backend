package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import java.math.BigDecimal;

@Entity
@Table(name = "Costume_Surcharge")
@Data
public class CostumeSurcharge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "costume_id")
    private Costume costume;

    private String name;
    private String description;
    private BigDecimal price;
}