package com.padeladmin.padeladmin.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "zone_pairs",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"zone_id", "pair_id"}),
                @UniqueConstraint(columnNames = {"zone_id", "position"})
        })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ZonePair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pair_id", nullable = false)
    private Pair pair;

    // 1 = cabeza de zona (seed), 2, 3, 4
    // La posición importa para los partidos en zonas de 4: 1° vs 4°, 2° vs 3°
    @Column(nullable = false)
    private Integer position;
}
