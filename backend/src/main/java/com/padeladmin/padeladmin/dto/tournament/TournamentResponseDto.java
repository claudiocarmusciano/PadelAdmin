package com.padeladmin.padeladmin.dto.tournament;

import com.padeladmin.padeladmin.enums.TournamentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
    private boolean fixtureGenerated;   // true si ya se generó el fixture de zona
    private boolean hasResults;         // true si hay al menos un partido con resultado cargado (PLAYED)
    private List<Integer> zoneDays;      // días de semana habilitados (1=Lun … 7=Dom), vacío = todos
    private LocalDateTime createdAt;
}
