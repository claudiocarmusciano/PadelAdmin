package com.padeladmin.padeladmin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Entity
@Table(name = "tournament_buffers")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TournamentBuffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    // 0 = Lunes, 6 = Domingo
    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "buffer_start", nullable = false)
    private LocalTime bufferStart;

    @Column(name = "buffer_end", nullable = false)
    private LocalTime bufferEnd;
}
