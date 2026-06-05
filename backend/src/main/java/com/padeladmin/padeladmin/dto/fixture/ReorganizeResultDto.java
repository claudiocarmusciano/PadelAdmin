package com.padeladmin.padeladmin.dto.fixture;

import lombok.Builder;
import lombok.Data;

/** Resultado de reordenar zonas para intentar programar todos los partidos. */
@Data
@Builder
public class ReorganizeResultDto {
    private boolean solved;            // true si se llegó a 0 partidos sin programar
    private int pending;               // partidos sin programar tras el mejor arreglo
    private String swapApplied;        // descripción del intercambio aplicado (null si no se cambió nada)
    private boolean suggestMoreCourts; // sugerir sumar canchas/otro complejo
    private String message;            // mensaje listo para mostrar al usuario
    private FixtureResponseDto fixture; // fixture resultante (con el mejor arreglo)
}
