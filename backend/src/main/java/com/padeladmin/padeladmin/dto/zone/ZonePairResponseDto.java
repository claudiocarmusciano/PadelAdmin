package com.padeladmin.padeladmin.dto.zone;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ZonePairResponseDto {
    private Integer position;
    private Long pairId;
    private Integer totalPoints;
    private String player1;
    private String player2;
}
