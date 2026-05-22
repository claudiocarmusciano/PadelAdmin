package com.padeladmin.padeladmin.dto.match;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetScoreDto {

    @NotNull(message = "Los games de la pareja 1 son obligatorios")
    @Min(value = 0, message = "Los games no pueden ser negativos")
    private Integer pair1Games;

    @NotNull(message = "Los games de la pareja 2 son obligatorios")
    @Min(value = 0, message = "Los games no pueden ser negativos")
    private Integer pair2Games;
}
