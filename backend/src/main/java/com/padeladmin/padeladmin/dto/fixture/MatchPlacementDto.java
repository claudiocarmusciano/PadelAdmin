package com.padeladmin.padeladmin.dto.fixture;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/** Un destino posible para mover un partido: cancha + fecha + hora, con su validez. */
@Data
@Builder
public class MatchPlacementDto {
    private Long courtId;
    private String courtName;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;
    private boolean valid;     // true = se puede ubicar acá (verde); false = no (rojo)
    private boolean current;   // true = es la posición actual del partido
    private String reason;     // motivo si es inválido (para tooltip), null si es válido
}
