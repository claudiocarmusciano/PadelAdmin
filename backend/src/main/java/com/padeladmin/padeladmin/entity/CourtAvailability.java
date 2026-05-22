package com.padeladmin.padeladmin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Entity
@Table(name = "court_availability",
        uniqueConstraints = @UniqueConstraint(columnNames = {"court_id", "day_of_week"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CourtAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    // 0 = Lunes, 6 = Domingo
    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;
}
