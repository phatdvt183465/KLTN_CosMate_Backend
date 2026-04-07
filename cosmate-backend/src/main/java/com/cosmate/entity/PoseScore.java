package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Pose_Scores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PoseScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "cosplayer_id")
    private Integer cosplayerId;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "score", precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Column(name = "character_name")
    private String characterName;

    @Column(name = "comment")
    private String comment;
}