package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Nationalized;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Costumes")
@Data
public class Costume {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "provider_id")
    private Integer providerId;

    @Nationalized
    private String name;

    private String size;

    @Column(name = "rent_purpose")
    private String rentPurpose;

    @Column(name = "number_of_items")
    private Integer numberOfItems;

    @Nationalized
    private String description;

    @Column(name = "price_per_day")
    private BigDecimal pricePerDay;

    @Column(name = "deposit_amount")
    private BigDecimal depositAmount;

    private String status;

    @OneToMany(mappedBy = "costume", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CostumeImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "costume", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CostumeSurcharge> surcharges = new ArrayList<>();

    @OneToMany(mappedBy = "costume", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CostumeAccessory> accessories = new ArrayList<>();

    @OneToMany(mappedBy = "costume", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CostumeRentalOption> rentalOptions = new ArrayList<>();

    @Column(name = "costume_vector", columnDefinition = "NVARCHAR(MAX)")
    private String costumeVector;
}