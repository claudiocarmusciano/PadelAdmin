package com.padeladmin.padeladmin.entity;

import com.padeladmin.padeladmin.enums.MatchPhase;
import com.padeladmin.padeladmin.enums.MatchStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "matches")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private MatchPhase phase;

    // Solo para partidos de zona
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    // Solo para partidos eliminatorios: 1=Final, 2=Semis, 4=Cuartos, 8=Octavos
    @Column(name = "elimination_round")
    private Integer eliminationRound;

    // Solo para zonas de 4: 1=primera ronda, 2=segunda ronda (ganadores vs ganadores / perdedores vs perdedores)
    @Column(name = "zone_round")
    private Integer zoneRound;

    // Solo para zona de 4 ronda 2: WINNERS (gan vs gan) o LOSERS (perd vs perd).
    // Las parejas quedan null hasta que se cierra la Ronda 1 de la zona.
    @Enumerated(EnumType.STRING)
    @Column(name = "zone_round2_type", length = 10)
    private com.padeladmin.padeladmin.enums.ZoneRound2Type zoneRound2Type;

    // Posición en el cuadro para reconstruir el draw
    @Column(name = "bracket_slot")
    private Integer bracketSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pair1_id")
    private Pair pair1;

    // Null cuando is_bye = true
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pair2_id")
    private Pair pair2;

    @Column(name = "is_bye", nullable = false)
    @Builder.Default
    private boolean bye = false;

    // Null hasta que el admin asigne cancha y horario
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id")
    private Court court;

    @Column(name = "scheduled_start")
    private LocalDateTime scheduledStart;

    @Column(name = "scheduled_end")
    private LocalDateTime scheduledEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MatchStatus status = MatchStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
