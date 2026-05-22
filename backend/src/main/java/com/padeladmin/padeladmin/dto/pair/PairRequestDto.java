package com.padeladmin.padeladmin.dto.pair;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PairRequestDto {

    @NotNull(message = "Los jugadores son obligatorios")
    @Size(min = 2, max = 2, message = "Una pareja debe tener exactamente 2 jugadores")
    private List<Long> playerIds;
}
