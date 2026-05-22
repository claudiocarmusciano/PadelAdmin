package com.padeladmin.padeladmin.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pair_players",
        uniqueConstraints = @UniqueConstraint(columnNames = {"pair_id", "player_id"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PairPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pair_id", nullable = false)
    private Pair pair;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;
}
