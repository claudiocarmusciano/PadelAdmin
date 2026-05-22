package com.padeladmin.padeladmin.dto.tournament;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TournamentRequestDto {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
    private String name;

    @NotNull(message = "La fecha de inicio es obligatoria")
    private LocalDate startDate;

    @NotNull(message = "La fecha de fin es obligatoria")
    private LocalDate endDate;

    @NotNull(message = "La categoría es obligatoria")
    private Long categoryId;

    private Long complexId;

    @Min(value = 30, message = "La duración mínima de un partido es 30 minutos")
    private Integer matchDurationMinutes = 60;

    @Min(value = 60, message = "El intervalo mínimo entre partidos es 60 minutos")
    private Integer minIntervalMinutes = 180;
}
