package com.padeladmin.padeladmin.dto.elimination;

import com.padeladmin.padeladmin.dto.fixture.MatchPairDto;
import com.padeladmin.padeladmin.enums.MatchStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EliminationMatchDto {
    private Long id;
    private Integer eliminationRound;  // 8=R16, 4=QF, 2=SF, 1=Final
    private String roundName;          // "Cuartos de Final", "Semifinales", etc.
    private Integer bracketSlot;       // posición dentro de la ronda (1, 2, 3...)
    private MatchPairDto pair1;        // null si aún no se conoce el clasificado
    private MatchPairDto pair2;        // null si aún no se conoce el clasificado
    private boolean bye;
    private String courtName;
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    private MatchStatus status;
}
