package com.padeladmin.padeladmin.dto.tournament;

import lombok.Builder;
import lombok.Data;

import java.time.LocalTime;

@Data
@Builder
public class TournamentBufferResponseDto {
    private Long id;
    private Integer dayOfWeek;
    private String dayName;
    private LocalTime bufferStart;
    private LocalTime bufferEnd;
}
