package com.padeladmin.padeladmin.dto.pair;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PairPlayerResponseDto {
    private Long id;
    private String firstName;
    private String lastName;
    private String phone;
    private Integer points; // puntos del jugador en la categoría del torneo
}
