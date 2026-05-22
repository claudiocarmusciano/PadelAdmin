package com.padeladmin.padeladmin.dto.pair;

import com.padeladmin.padeladmin.enums.ConstraintType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class PairScheduleConstraintRequestDto {

    @NotNull(message = "El tipo de restricción es obligatorio")
    private ConstraintType constraintType;

    @NotNull(message = "El día de la semana es obligatorio")
    @Min(value = 0, message = "El día debe estar entre 0 (Lunes) y 6 (Domingo)")
    @Max(value = 6, message = "El día debe estar entre 0 (Lunes) y 6 (Domingo)")
    private Integer dayOfWeek;

    @NotNull(message = "La hora de inicio es obligatoria")
    private LocalTime slotStart;

    @NotNull(message = "La hora de fin es obligatoria")
    private LocalTime slotEnd;
}
