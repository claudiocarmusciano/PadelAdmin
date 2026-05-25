package com.padeladmin.padeladmin.dto.pair;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PairResponseDto {
    private Long id;
    private Long tournamentId;
    private Double totalPoints;
    private List<PairPlayerResponseDto> players;
    private List<PairScheduleConstraintResponseDto> constraints;
}
