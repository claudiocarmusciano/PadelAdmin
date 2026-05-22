package com.padeladmin.padeladmin.dto.match;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SetScoreResponseDto {
    private Integer setNumber;
    private Integer pair1Games;
    private Integer pair2Games;
    private Long winnerPairId; // id de la pareja que ganó este set
}
