package com.padeladmin.padeladmin.dto.player;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Categoría de un jugador con su posición en el ranking de esa categoría. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerCategoryRankDto {
    private Long categoryId;
    private String categoryName;
    /** Club dueño de la categoría/ranking (null = categoría global, anterior al multi-tenant). */
    private String clubName;
    private Double points;
    /** Posición #N dentro de la categoría (1 = mejor). Empates comparten posición. */
    private Integer rank;
    /** Total de jugadores con puntos en esta categoría. */
    private Integer totalInCategory;
}
