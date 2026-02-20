package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import java.time.LocalDateTime;

@Entity
@Table(name = "Disputes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "created_by_user_id")
    private Integer createdByUserId;

    @Column(name = "staff_id")
    private Integer staffId;

    @Column(columnDefinition = "nvarchar(max)")
    private String reason;

    @Column(length = 50)
    private String status; // e.g. OPEN, RESOLVED

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToOne(mappedBy = "dispute", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    private com.cosmate.entity.DisputeResult result;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
