package com.cosmate.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Dispute_Images")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "dispute_image_url", columnDefinition = "nvarchar(max)")
    private String disputeImageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispute_id")
    @JsonBackReference
    private Dispute dispute;
}

