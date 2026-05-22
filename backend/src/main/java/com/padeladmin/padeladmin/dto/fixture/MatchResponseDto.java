package com.padeladmin.padeladmin.dto.fixture;

import com.padeladmin.padeladmin.enums.MatchStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MatchResponseDto {
    private Long id;
    private String zoneName;
    private Integer eliminationRound;
    private MatchPairDto pair1;
    private MatchPairDto pair2;
    private String courtName;
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    private MatchStatus status;
}
