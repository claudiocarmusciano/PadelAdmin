package com.padeladmin.padeladmin.dto.player;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlayerCategoryPointsResponseDto {
    private Long id;
    private Long playerId;
    private String playerName;
    private Long categoryId;
    private String categoryName;
    /** Club dueño de la categoría/ranking (null = categoría global, anterior al multi-tenant). */
    private String clubName;
    private Double points;
}
