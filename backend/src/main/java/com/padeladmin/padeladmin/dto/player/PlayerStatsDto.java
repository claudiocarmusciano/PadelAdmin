package com.padeladmin.padeladmin.dto.player;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Estadísticas agregadas de un jugador a lo largo de toda su historia. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerStatsDto {

    private Long playerId;
    private String firstName;
    private String lastName;

    // ── Torneos ────────────────────────────────────────────────
    private int tournamentsPlayed;
    private int tournamentsWon;
    private int tournamentsFinalist;
    private int tournamentsSemifinalist;

    // ── Partidos ───────────────────────────────────────────────
    private int matchesPlayed;
    private int matchesWon;
    private int matchesLost;
    private int walkoversReceived; // rival no se presentó (gana sin jugar)
    private int walkoversGiven;    // este jugador no se presentó

    // ── Sets ───────────────────────────────────────────────────
    private int setsPlayed;
    private int setsWon;
    private int setsLost;

    // ── Games ──────────────────────────────────────────────────
    private int gamesPlayed;
    private int gamesWon;
    private int gamesLost;

    // ── Detalle ────────────────────────────────────────────────
    /** Top 5 compañeros más frecuentes. */
    private List<PartnerStatDto> topPartners;

    /** Historial de torneos jugados (ordenado por fecha DESC). */
    private List<TournamentParticipationDto> tournamentHistory;
}
