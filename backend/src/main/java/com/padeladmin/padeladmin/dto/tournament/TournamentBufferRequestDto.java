package com.padeladmin.padeladmin.dto.tournament;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class TournamentBufferRequestDto {

    @NotNull(message = "El día de la semana es obligatorio")
    @Min(value = 0, message = "El día debe estar entre 0 (Lunes) y 6 (Domingo)")
    @Max(value = 6, message = "El día debe estar entre 0 (Lunes) y 6 (Domingo)")
    private Integer dayOfWeek;

    @NotNull(message = "La hora de inicio del pulmón es obligatoria")
    private LocalTime bufferStart;

    @NotNull(message = "La hora de fin del pulmón es obligatoria")
    private LocalTime bufferEnd;
}
