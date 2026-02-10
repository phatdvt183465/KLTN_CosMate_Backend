package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Nationalized;
import java.math.BigDecimal;

@Entity
@Table(name = "Costume_Accessories")
@Data
public class CostumeAccessory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "costume_id")
    private Costume costume;

    @Nationalized
    private String name;

    @Nationalized
    private String description;

    private BigDecimal price;

    @Column(name = "is_required")
    private Boolean isRequired;

    private String status;
}