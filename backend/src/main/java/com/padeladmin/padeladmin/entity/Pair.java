package com.padeladmin.padeladmin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pairs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Pair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    // Suma de puntos de ambos jugadores en la categoría elegida
    @Column(name = "total_points", nullable = false)
    @Builder.Default
    private Double totalPoints = 0.0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "pair", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PairPlayer> players = new ArrayList<>();

    @OneToMany(mappedBy = "pair", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PairScheduleConstraint> constraints = new ArrayList<>();
}
