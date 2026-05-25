package com.padeladmin.padeladmin.dto.fixture;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.padeladmin.padeladmin.enums.MatchStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class MatchResponseDto {
    private Long id;
    private String zoneName;
    private Integer eliminationRound;
    private MatchPairDto pair1;
    private MatchPairDto pair2;
    private Long courtId;
    private String courtName;
    private String complexName;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime scheduledStart;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime scheduledEnd;
    private MatchStatus status;

    // Resultado (solo cuando status == PLAYED)
    private Long winnerPairId;
    private List<SetScoreDto> sets;

    @Data
    @Builder
    public static class SetScoreDto {
        private int pair1Games;
        private int pair2Games;
    }
}
