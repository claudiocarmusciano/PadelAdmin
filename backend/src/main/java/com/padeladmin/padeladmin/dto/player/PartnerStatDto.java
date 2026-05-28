package com.padeladmin.padeladmin.dto.player;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Compañero con el que el jugador ha formado pareja. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerStatDto {
    private Long partnerId;
    private String partnerName;       // "Apellido, Nombre"
    private int tournamentsTogether;  // cuántos torneos jugaron juntos
    private int matchesTogether;      // cuántos partidos jugaron juntos
    private int matchesWon;           // cuántos ganaron juntos
}
