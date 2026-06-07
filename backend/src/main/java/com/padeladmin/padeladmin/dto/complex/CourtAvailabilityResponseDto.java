package com.padeladmin.padeladmin.dto.complex;

import lombok.Builder;
import lombok.Data;

import java.time.LocalTime;

@Data
@Builder
public class CourtAvailabilityResponseDto {
    private Long id;
    private Integer dayOfWeek;
    private String dayName;
    private LocalTime openTime;
    private LocalTime closeTime;
    private LocalTime breakStart;   // pulmón opcional
    private LocalTime breakEnd;
}
