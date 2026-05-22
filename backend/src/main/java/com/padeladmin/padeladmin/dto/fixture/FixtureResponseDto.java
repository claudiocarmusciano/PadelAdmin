package com.padeladmin.padeladmin.dto.fixture;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FixtureResponseDto {
    private Long tournamentId;
    private int totalMatches;
    private int scheduledCount;
    private int pendingCount;
    private List<MatchResponseDto> matches;
}
