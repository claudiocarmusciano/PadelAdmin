package com.padeladmin.padeladmin.dto.pair;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PairPlayerEntryDto {

    @NotNull(message = "El id de jugador es obligatorio")
    private Long playerId;

    /** Categoría elegida para los puntos de este jugador. Obligatoria. */
    @NotNull(message = "La categoría del jugador es obligatoria")
    private Long categoryId;
}
