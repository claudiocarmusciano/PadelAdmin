package com.padeladmin.padeladmin.dto.fixture;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MatchPairDto {
    private Long id;
    private String player1;
    private String player2;
    private Double totalPoints;
}
