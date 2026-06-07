package com.padeladmin.padeladmin.dto.complex;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class CourtAvailabilityRequestDto {

    @NotNull(message = "El día de la semana es obligatorio")
    @Min(value = 0, message = "El día debe estar entre 0 (Lunes) y 6 (Domingo)")
    @Max(value = 6, message = "El día debe estar entre 0 (Lunes) y 6 (Domingo)")
    private Integer dayOfWeek;

    @NotNull(message = "La hora de apertura es obligatoria")
    private LocalTime openTime;

    @NotNull(message = "La hora de cierre es obligatoria")
    private LocalTime closeTime;

    // Pulmón horario opcional (ambos null = sin pulmón)
    private LocalTime breakStart;
    private LocalTime breakEnd;
}
