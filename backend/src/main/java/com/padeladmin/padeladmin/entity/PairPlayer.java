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

    /**
     * Categoría con la que este jugador aporta sus puntos a la pareja.
     * Nullable: si es null se usa la categoría del torneo como fallback.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;
}
