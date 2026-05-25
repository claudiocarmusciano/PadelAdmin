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
    private Double points;
}
