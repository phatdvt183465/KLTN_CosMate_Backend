package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Nationalized;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Services")
@Data
public class Service {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "provider_id")
    private Integer providerId;

    @Nationalized
    @Column(name = "service_name")
    private String serviceName;

    @Nationalized
    @Column(name = "service_type")
    private String serviceType;

    @Nationalized
    @Column(name = "description")
    private String description;

    @Column(name = "slot_duration_hours")
    private Integer slotDurationHours;

    @Column(name = "price_per_slot")
    private BigDecimal pricePerSlot;

    @Column(name = "equipment_depreciation_cost")
    private BigDecimal equipmentDepreciationCost;

    @Column(name = "status")
    private String status;

    @Column(name = "min_price")
    private BigDecimal minPrice;

    @Column(name = "max_price")
    private BigDecimal maxPrice;

    @Column(name = "deposit_amount")
    private BigDecimal depositAmount;

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ServiceArea> areas = new ArrayList<>();

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ServiceAlbum> albums = new ArrayList<>();
}