package com.padeladmin.padeladmin.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "match_sets")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MatchSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_result_id", nullable = false)
    private MatchResult matchResult;

    // Número de set: 1, 2, 3
    @Column(name = "set_number", nullable = false)
    private Integer setNumber;

    // Games ganados por cada pareja en este set
    @Column(name = "pair1_games", nullable = false)
    private Integer pair1Games;

    @Column(name = "pair2_games", nullable = false)
    private Integer pair2Games;
}
