package com.padeladmin.padeladmin.dto.tournament;

import com.padeladmin.padeladmin.enums.TournamentStatus;
import jakarta.validation.constraints.NotNull;

/** Body para cambiar el estado de un torneo: { "status": "COMPLETED" } */
public record StatusUpdateRequest(@NotNull TournamentStatus status) {
}
