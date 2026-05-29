package com.padeladmin.padeladmin.entity;

import com.padeladmin.padeladmin.enums.TournamentStage;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registro histórico de puntos otorgados a un jugador por su desempeño en un torneo.
 *
 * Es deliberadamente "plano" (sin FKs duras a Tournament/Player/Category): guarda los IDs
 * como columnas Long más un snapshot del nombre. Así el historial SOBREVIVE aunque el torneo
 * se borre (el delete nativo de TournamentService no toca esta tabla) o se haga una limpieza
 * de puntos al arrancar una nueva temporada.
 */
@Entity
@Table(name = "tournament_point_awards")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TournamentPointAward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tournament_id")
    private Long tournamentId;

    @Column(name = "tournament_name", length = 150)
    private String tournamentName;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "category_name", length = 100)
    private String categoryName;

    @Column(name = "player_id")
    private Long playerId;

    @Column(name = "player_name", length = 150)
    private String playerName;

    @Column(name = "pair_id")
    private Long pairId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", length = 30)
    private TournamentStage stage;

    @Column(name = "points", nullable = false, precision = 6, scale = 2)
    private BigDecimal points;

    @CreationTimestamp
    @Column(name = "awarded_at", nullable = false, updatable = false)
    private LocalDateTime awardedAt;
}
