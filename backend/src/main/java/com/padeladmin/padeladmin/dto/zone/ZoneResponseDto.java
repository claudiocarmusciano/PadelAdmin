package com.padeladmin.padeladmin.dto.zone;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ZoneResponseDto {
    private Long id;
    private String name;
    private Integer zoneSize;
    private Integer zoneOrder;
    private Integer totalZonePoints;
    private List<ZonePairResponseDto> pairs;
}
