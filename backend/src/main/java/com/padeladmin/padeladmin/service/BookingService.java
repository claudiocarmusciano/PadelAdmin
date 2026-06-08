package com.padeladmin.padeladmin.service;

import com.padeladmin.padeladmin.dto.booking.AvailableSlotDto;
import com.padeladmin.padeladmin.dto.booking.CourtBookingRequestDto;
import com.padeladmin.padeladmin.dto.booking.CourtBookingResponseDto;
import com.padeladmin.padeladmin.entity.Court;
import com.padeladmin.padeladmin.entity.CourtAvailability;
import com.padeladmin.padeladmin.entity.CourtBooking;
import com.padeladmin.padeladmin.entity.Match;
import com.padeladmin.padeladmin.enums.BookingSource;
import com.padeladmin.padeladmin.enums.BookingStatus;
import com.padeladmin.padeladmin.exception.BusinessException;
import com.padeladmin.padeladmin.exception.ResourceNotFoundException;
import com.padeladmin.padeladmin.repository.CourtAvailabilityRepository;
import com.padeladmin.padeladmin.repository.CourtBookingRepository;
import com.padeladmin.padeladmin.repository.CourtRepository;
import com.padeladmin.padeladmin.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class BookingService {

    private final CourtBookingRepository bookingRepository;
    private final CourtRepository courtRepository;
    private final CourtAvailabilityRepository availabilityRepository;
    private final MatchRepository matchRepository;

    private static final int SLOT_STEP_MINUTES = 30;

    /**
     * Obtener slots disponibles para una cancha en una fecha específica
     *
     * Algoritmo (reutilizado de FixtureService.generateSlotsForDay):
     * 1. Validar que la cancha existe
     * 2. Obtener CourtAvailability para el día de la semana
     * 3. Si no hay disponibilidad → retornar lista vacía
     * 4. Generar candidatos: openTime → closeTime, paso 30min
     * 5. Para cada candidato: validar contra pulmón, bookings confirmadas, matches
     * 6. Retornar slots disponibles
     */
    public List<AvailableSlotDto> getAvailableSlots(Long courtId, LocalDate date) {
        // Validar cancha
        Court court = courtRepository.findById(courtId)
            .orElseThrow(() -> new ResourceNotFoundException("Court", courtId));

        int slotDurationMinutes = court.getSlotDurationMinutes() != null
            ? court.getSlotDurationMinutes()
            : 90;

        // Obtener disponibilidad por día de semana
        int dowJava = date.getDayOfWeek().getValue();  // 1=Lun..7=Dom
        int dowStorage = dowJava - 1;  // 0=Lun..6=Dom (convención CourtAvailability)

        CourtAvailability availability = availabilityRepository
            .findByCourtIdAndDayOfWeek(courtId, dowStorage)
            .orElse(null);

        if (availability == null) {
            return new ArrayList<>();  // No hay horario definido para este día
        }

        List<AvailableSlotDto> availableSlots = new ArrayList<>();

        // Generar candidatos: iterar de openTime a closeTime, paso 30 min
        LocalTime cursor = availability.getOpenTime();
        LocalTime closeTime = availability.getCloseTime();
        LocalTime breakStart = availability.getBreakStart();
        LocalTime breakEnd = availability.getBreakEnd();
        boolean hasBreak = breakStart != null && breakEnd != null;

        while (!cursor.isAfter(closeTime.minusMinutes(slotDurationMinutes))) {
            LocalTime slotEnd = cursor.plusMinutes(slotDurationMinutes);

            // Validar: no solapamiento con pulmón
            boolean overlapsBreak = hasBreak && timesOverlap(cursor, slotEnd, breakStart, breakEnd);

            // Validar: no solapamiento con bookings confirmadas
            long conflictingBookings = bookingRepository.countConflictingBookings(
                courtId, date, cursor, slotEnd
            );

            // Validar: no solapamiento con matches de torneo
            LocalDateTime slotStartDt = LocalDateTime.of(date, cursor);
            LocalDateTime slotEndDt = LocalDateTime.of(date, slotEnd);
            List<Match> conflictingMatches = matchRepository.findConflictingMatches(
                courtId, date, slotStartDt, slotEndDt
            );

            boolean isAvailable = !overlapsBreak && conflictingBookings == 0 && conflictingMatches.isEmpty();
            String reason = null;
            if (overlapsBreak) {
                reason = "Ocupado por pulmón horario";
            } else if (conflictingBookings > 0) {
                reason = "Ocupado por reserva confirmada";
            } else if (!conflictingMatches.isEmpty()) {
                reason = "Ocupado por partido de torneo";
            }

            availableSlots.add(AvailableSlotDto.builder()
                .startTime(cursor)
                .endTime(slotEnd)
                .available(isAvailable)
                .reason(reason)
                .build());

            cursor = cursor.plusMinutes(SLOT_STEP_MINUTES);
        }

        return availableSlots;
    }

    /**
     * Helper: validar solapamiento de rangos de tiempo
     * Reutilizado de FixtureService.timesOverlap()
     */
    private boolean timesOverlap(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        return start1.isBefore(end2) && end1.isAfter(start2);
    }

    /**
     * Crear una nueva reserva (pública o admin)
     *
     * Validaciones:
     * 1. Cancha existe y está activa
     * 2. Fecha no es del pasado
     * 3. Horario dentro de disponibilidad de la cancha
     * 4. RE-VALIDAR: no hay conflicto (doble validación para race conditions)
     * 5. Campos requeridos presentes
     *
     * @return CourtBookingResponseDto
     * @throws BusinessException si hay conflicto o validación fallida
     */
    @Transactional
    public CourtBookingResponseDto createBooking(
        CourtBookingRequestDto requestDto,
        BookingSource source) {

        // Validar cancha
        Court court = courtRepository.findById(requestDto.getCourtId())
            .orElseThrow(() -> new ResourceNotFoundException("Court", requestDto.getCourtId()));

        if (!court.isActive()) {
            throw new BusinessException("La cancha no está disponible para reservas");
        }

        // Validar fecha no sea del pasado
        if (requestDto.getBookingDate().isBefore(LocalDate.now())) {
            throw new BusinessException("No se pueden hacer reservas para fechas pasadas");
        }

        // Validar horario dentro de disponibilidad
        int dow = requestDto.getBookingDate().getDayOfWeek().getValue() - 1;
        CourtAvailability availability = availabilityRepository
            .findByCourtIdAndDayOfWeek(requestDto.getCourtId(), dow)
            .orElseThrow(() -> new BusinessException(
                "La cancha no tiene horario disponible para este día de semana"
            ));

        if (requestDto.getStartTime().isBefore(availability.getOpenTime())
            || requestDto.getStartTime().isAfter(availability.getCloseTime())) {
            throw new BusinessException("El horario está fuera del rango permitido");
        }

        // RE-VALIDAR dentro de transacción para evitar race conditions
        long existingBookings = bookingRepository.countConflictingBookings(
            requestDto.getCourtId(),
            requestDto.getBookingDate(),
            requestDto.getStartTime(),
            requestDto.getStartTime().plusMinutes(
                court.getSlotDurationMinutes() != null ? court.getSlotDurationMinutes() : 90
            )
        );

        if (existingBookings > 0) {
            throw new BusinessException("El horario ya está ocupado. Por favor selecciona otro.");
        }

        // Crear y persistir
        CourtBooking booking = CourtBooking.builder()
            .court(court)
            .bookingDate(requestDto.getBookingDate())
            .startTime(requestDto.getStartTime())
            .endTime(requestDto.getStartTime().plusMinutes(
                court.getSlotDurationMinutes() != null ? court.getSlotDurationMinutes() : 90
            ))
            .customerName(requestDto.getCustomerName())
            .customerPhone(requestDto.getCustomerPhone())
            .notes(requestDto.getNotes())
            .status(BookingStatus.CONFIRMED)
            .source(source)
            .build();

        try {
            CourtBooking saved = bookingRepository.save(booking);
            return toResponseDto(saved);
        } catch (DataIntegrityViolationException e) {
            // Índice único disparado: alguien más reservó entre validación e insert
            log.warn("Unique constraint violation on booking: {}", e.getMessage());
            throw new BusinessException("El horario ya está ocupado. Por favor intenta con otro.");
        }
    }

    /**
     * Obtener reservas de un día (filtrable por complejo)
     */
    public List<CourtBookingResponseDto> getBookingsByComplexAndDate(
        Long complexId,
        LocalDate date) {

        return bookingRepository
            .findByComplexIdAndDateAndStatus(complexId, date, BookingStatus.CONFIRMED)
            .stream()
            .map(this::toResponseDto)
            .collect(Collectors.toList());
    }

    /**
     * Cancelar una reserva (soft delete: status='CANCELLED')
     */
    @Transactional
    public void cancelBooking(Long bookingId) {
        CourtBooking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
    }

    /**
     * Mapear entidad a DTO
     */
    private CourtBookingResponseDto toResponseDto(CourtBooking booking) {
        return CourtBookingResponseDto.builder()
            .id(booking.getId())
            .courtId(booking.getCourt().getId())
            .courtName(booking.getCourt().getName())
            .complexName(booking.getCourt().getComplex().getName())
            .bookingDate(booking.getBookingDate())
            .startTime(booking.getStartTime())
            .endTime(booking.getEndTime())
            .status(booking.getStatus())
            .source(booking.getSource())
            .customerName(booking.getCustomerName())
            .customerPhone(booking.getCustomerPhone())
            .notes(booking.getNotes())
            .createdAt(booking.getCreatedAt())
            .build();
    }
}
