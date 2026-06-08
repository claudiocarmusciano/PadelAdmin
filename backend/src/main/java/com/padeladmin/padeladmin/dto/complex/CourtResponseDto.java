package com.padeladmin.padeladmin.dto.complex;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CourtResponseDto {
    private Long id;
    private String name;
    private boolean active;
    private Integer slotDurationMinutes;
}
