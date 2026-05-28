package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

@Entity
@Table(name = "Reviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "order_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Order order;

    private Integer rating;

    @Nationalized
    @Column(columnDefinition = "NVARCHAR(1000)")
    private String comment;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Nationalized
    @Column(name = "provider_reply", columnDefinition = "NVARCHAR(1000)")
    private String providerReply;

    @Column(name = "replied_at")
    private LocalDateTime repliedAt;

    @Column(name = "replied_by_provider_id")
    private Integer repliedByProviderId;

    @Column(name = "ai_sentiment", length = 50)
    private String aiSentiment;

    @Column(name = "is_spam_or_toxic")
    private Boolean isSpamOrToxic;

    @Nationalized
    @Column(name = "ai_summary", columnDefinition = "NVARCHAR(500)")
    private String aiSummary;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

