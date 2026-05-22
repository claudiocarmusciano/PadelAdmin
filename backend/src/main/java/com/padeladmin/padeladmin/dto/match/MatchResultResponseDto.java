package com.padeladmin.padeladmin.dto.match;

import com.padeladmin.padeladmin.dto.fixture.MatchPairDto;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class MatchResultResponseDto {
    private Long matchId;
    private String zoneName;
    private Integer zoneRound;
    private MatchPairDto pair1;
    private MatchPairDto pair2;
    private Integer pair1Sets;       // sets ganados por pareja 1 (ej: 2)
    private Integer pair2Sets;       // sets ganados por pareja 2 (ej: 1)
    private List<SetScoreResponseDto> sets; // detalle por set (ej: 6-1, 6-4)
    private Long winnerPairId;
    private LocalDateTime recordedAt;
    private boolean round2Created;
}
