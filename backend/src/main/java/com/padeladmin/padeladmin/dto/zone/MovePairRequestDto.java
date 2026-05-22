package com.padeladmin.padeladmin.dto.zone;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MovePairRequestDto {

    @NotNull(message = "La zona destino es obligatoria")
    private Long targetZoneId;
}
