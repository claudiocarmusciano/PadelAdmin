package com.padeladmin.padeladmin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "match_results")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MatchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false, unique = true)
    private Match match;

    @Column(name = "pair1_score", nullable = false)
    private Integer pair1Score;

    @Column(name = "pair2_score", nullable = false)
    private Integer pair2Score;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_pair_id", nullable = false)
    private Pair winnerPair;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    // W.O.: true cuando una pareja no se presentó
    @Column(name = "walkover", nullable = false, columnDefinition = "boolean not null default false")
    @Builder.Default
    private boolean walkover = false;

    // Pareja que dio W.O. (null si no fue W.O.)
    @Column(name = "walkover_pair_id")
    private Long walkoverId;

    // Detalle de cada set jugado (2 o 3 sets)
    @OneToMany(mappedBy = "matchResult", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("setNumber ASC")
    @Builder.Default
    private List<MatchSet> sets = new ArrayList<>();
}
