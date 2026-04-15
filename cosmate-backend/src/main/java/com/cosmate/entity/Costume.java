package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Costumes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

    @Column(name = "rent_discount")
    private Integer rentDiscount;

    @Column(name = "deposit_amount")
    private BigDecimal depositAmount;

    private String status;

    @Column(name = "text_vector", columnDefinition = "NVARCHAR(MAX)")
    private String textVector;

    @Column(name = "image_vector", columnDefinition = "NVARCHAR(MAX)")
    private String imageVector;

    @Column(name = "completed_rent_count", nullable = false)
    @Builder.Default
    private Integer completedRentCount = 0;

    @OneToMany(mappedBy = "costume", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CostumeImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "costume", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CostumeSurcharge> surcharges = new ArrayList<>();

    @OneToMany(mappedBy = "costume", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CostumeAccessory> accessories = new ArrayList<>();

    @OneToMany(mappedBy = "costume", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CostumeRentalOption> rentalOptions = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "Costume_Character_Tags",
            joinColumns = @JoinColumn(name = "costume_id"),
            inverseJoinColumns = @JoinColumn(name = "character_id")
    )
    @Builder.Default
    private List<Character> characters = new ArrayList<>();
}
