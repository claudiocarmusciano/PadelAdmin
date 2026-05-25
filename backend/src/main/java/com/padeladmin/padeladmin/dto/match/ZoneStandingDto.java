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
    private Double totalPoints;       // Puntos de ranking (pre-torneo)
    private Integer tournamentPoints; // Puntos ganados en el torneo (2=ganó, 1=perdió presente, 0=W.O.)
    private Integer played;
    private Integer wins;
    private Integer losses;
    private Integer walkovers;        // Partidos perdidos por W.O. (no suman punto)
    private Integer setsFor;
    private Integer setsAgainst;
    private Integer setsDiff;
    private boolean classified;
}
