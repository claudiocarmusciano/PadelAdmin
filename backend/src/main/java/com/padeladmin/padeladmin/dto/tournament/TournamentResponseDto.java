package com.padeladmin.padeladmin.dto.tournament;

import com.padeladmin.padeladmin.enums.TournamentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class TournamentResponseDto {
    private Long id;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long categoryId;
    private String categoryName;
    private Long complexId;
    private String complexName;
    private Integer matchDurationMinutes;
    private Integer minIntervalMinutes;
    private TournamentStatus status;
    private LocalDateTime createdAt;
}
