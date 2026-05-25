package com.padeladmin.padeladmin.dto.zone;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SwapPairsRequestDto {

    @NotNull(message = "La pareja destino es obligatoria")
    private Long targetPairId;
}
