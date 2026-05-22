package com.padeladmin.padeladmin.dto.match;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ZoneStandingDto {
    private Integer position;
    private Long pairId;
    private String player1;
    private String player2;
    private Integer totalPoints;
    private Integer played;
    private Integer wins;
    private Integer losses;
    private Integer setsFor;
    private Integer setsAgainst;
    private Integer setsDiff;
    private boolean classified;
}
