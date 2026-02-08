package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Nationalized;
import java.math.BigDecimal;
import java.util.List;

public class Costume {
    @Entity
    @Table(name = "Costumes")
    @Data
    public class Costume {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Nationalized
        @Column(nullable = false)
        private String name;

        @Nationalized
        private String description;

        private String size; // S, M, L, XL...

        private BigDecimal price; // Dùng BigDecimal cho tiền tệ nhé

        private BigDecimal deposit; // Tiền cọc

        private Integer status; // 0: Available, 1: Rented, 2: Maintenance

        @ManyToOne
        @JoinColumn(name = "user_id")
        private User owner; // Người sở hữu bộ đồ

        @OneToMany(mappedBy = "costume", cascade = CascadeType.ALL)
        private List<CostumeImage> images;

        @OneToMany(mappedBy = "costume", cascade = CascadeType.ALL)
        private List<CostumeSurcharge> surcharges;
    }
}
