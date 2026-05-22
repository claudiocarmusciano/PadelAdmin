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
    // Matches agrupados por ronda: clave = eliminationRound (8, 4, 2, 1)
    // ordenadas de mayor (R16) a menor (Final)
    private Map<Integer, List<EliminationMatchDto>> rounds;
}
