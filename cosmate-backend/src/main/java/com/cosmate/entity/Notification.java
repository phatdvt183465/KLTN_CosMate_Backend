package com.cosmate.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Nationalized;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "Notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 50)
    private String type;

    @Column(length = 255)
    private String header;

    @Lob
    @Nationalized
    private String content;

    @Column(name = "send_at")
    private LocalDateTime sendAt;

    @Column(name = "is_read")
    private Boolean isRead;
}

