package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Event_Participants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_participant_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(name = "cosplayer_id")
    private Integer cosplayerId;

    @Column(name = "submission_image_url")
    private String submissionImageUrl;

    @OneToMany(mappedBy = "participant", cascade = CascadeType.ALL)
    private List<Vote> votes = new ArrayList<>();
}