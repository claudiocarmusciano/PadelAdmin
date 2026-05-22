package com.padeladmin.padeladmin.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Builder
public class TimeSlot {
    private final LocalDate date;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final Long courtId;
}
