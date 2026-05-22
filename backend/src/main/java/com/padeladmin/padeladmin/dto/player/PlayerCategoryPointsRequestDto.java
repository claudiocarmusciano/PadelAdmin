package com.padeladmin.padeladmin.dto.player;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlayerCategoryPointsRequestDto {

    @NotNull(message = "El ID de categoría es obligatorio")
    private Long categoryId;

    @NotNull(message = "Los puntos son obligatorios")
    @Min(value = 0, message = "Los puntos no pueden ser negativos")
    private Integer points;
}
