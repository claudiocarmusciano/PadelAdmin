package com.padeladmin.padeladmin.dto.booking;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.padeladmin.padeladmin.enums.BookingSource;
import com.padeladmin.padeladmin.enums.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourtBookingResponseDto {

    private Long id;
    private Long courtId;
    private String courtName;
    private String complexName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate bookingDate;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    private BookingStatus status;
    private BookingSource source;
    private String customerName;
    private String customerPhone;
    private String notes;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
