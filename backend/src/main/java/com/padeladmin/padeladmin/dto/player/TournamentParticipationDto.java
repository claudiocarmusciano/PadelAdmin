package com.padeladmin.padeladmin.dto.player;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Participación de un jugador en un torneo: cómo le fue y cuál fue su mejor instancia. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentParticipationDto {
    private Long tournamentId;
    private String tournamentName;
    private String categoryName;
    private LocalDate startDate;
    private String tournamentStatus; // DRAFT | ACTIVE | COMPLETED
    /**
     * Mejor instancia alcanzada:
     *   "CHAMPION" | "FINALIST" | "SEMIFINAL" | "QUARTERFINAL" |
     *   "ROUND_8" | "ROUND_16" | "ROUND_32" | "ZONE" | "PARTICIPANT"
     */
    private String bestStage;
    /** Nombre del compañero en ese torneo (o "varios" si jugó con distintos). */
    private String partnerName;
    private int matchesPlayed;
    private int matchesWon;
}
