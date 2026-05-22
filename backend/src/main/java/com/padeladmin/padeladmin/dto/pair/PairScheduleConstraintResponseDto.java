package com.padeladmin.padeladmin.dto.pair;

import com.padeladmin.padeladmin.enums.ConstraintType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalTime;

@Data
@Builder
public class PairScheduleConstraintResponseDto {
    private Long id;
    private ConstraintType constraintType;
    private Integer dayOfWeek;
    private String dayName; // "Lunes", "Martes", etc.
    private LocalTime slotStart;
    private LocalTime slotEnd;
}
