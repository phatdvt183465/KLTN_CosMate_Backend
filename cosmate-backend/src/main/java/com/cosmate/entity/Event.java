package com.cosmate.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String status; // changed code: status is now a String to match the DB NVARCHAR(50) values like 'UPCOMING', 'ONGOING', etc.

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "created_by")
    private Integer createdBy;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL)
    private List<EventParticipant> participants = new ArrayList<>();
}