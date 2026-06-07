package com.padeladmin.padeladmin.dto.elimination;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class EliminationBracketDto {
    private Long tournamentId;
    private Integer totalClassified;
    private Integer bracketSize;
    // true = vista previa estructural (zonas sin terminar; los cruces son tentativos por posición de zona)
    private boolean preview;
    // true = el bracket real ya generado quedó desactualizado respecto a la clasificación actual
    // de las zonas (ej: se corrigió un resultado de zona). Conviene "Regenerar bracket".
    private boolean stale;
    // Matches agrupados por ronda: clave = eliminationRound (8, 4, 2, 1)
    // ordenadas de mayor (R16) a menor (Final)
    private Map<Integer, List<EliminationMatchDto>> rounds;
}
