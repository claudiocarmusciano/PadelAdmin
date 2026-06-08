package com.padeladmin.padeladmin.repository;

import com.padeladmin.padeladmin.entity.CourtBooking;
import com.padeladmin.padeladmin.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface CourtBookingRepository extends JpaRepository<CourtBooking, Long> {

    List<CourtBooking> findByCourtIdAndBookingDateAndStatus(
        Long courtId,
        LocalDate bookingDate,
        BookingStatus status
    );

    List<CourtBooking> findByCourtIdAndBookingDate(
        Long courtId,
        LocalDate bookingDate
    );

    List<CourtBooking> findByCourtIdAndBookingDateBetween(
        Long courtId,
        LocalDate startDate,
        LocalDate endDate
    );

    // Para admin: filtrar por complejo y fecha
    @Query("""
        SELECT cb FROM CourtBooking cb
        JOIN cb.court c
        WHERE c.complex.id = :complexId
        AND cb.bookingDate = :bookingDate
        AND cb.status = :status
        ORDER BY c.name, cb.startTime
    """)
    List<CourtBooking> findByComplexIdAndDateAndStatus(
        @Param("complexId") Long complexId,
        @Param("bookingDate") LocalDate bookingDate,
        @Param("status") BookingStatus status
    );

    // Check existencia de conflicto sin guardar
    @Query("""
        SELECT COUNT(cb) FROM CourtBooking cb
        WHERE cb.court.id = :courtId
        AND cb.bookingDate = :bookingDate
        AND cb.status = 'CONFIRMED'
        AND cb.startTime < :endTime
        AND cb.endTime > :startTime
    """)
    long countConflictingBookings(
        @Param("courtId") Long courtId,
        @Param("bookingDate") LocalDate bookingDate,
        @Param("startTime") LocalTime startTime,
        @Param("endTime") LocalTime endTime
    );
}
