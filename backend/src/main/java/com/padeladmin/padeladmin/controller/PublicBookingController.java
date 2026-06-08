package com.padeladmin.padeladmin.controller;

import com.padeladmin.padeladmin.dto.booking.AvailableSlotDto;
import com.padeladmin.padeladmin.dto.booking.CourtBookingRequestDto;
import com.padeladmin.padeladmin.dto.booking.CourtBookingResponseDto;
import com.padeladmin.padeladmin.dto.complex.CourtResponseDto;
import com.padeladmin.padeladmin.dto.complex.ComplexResponseDto;
import com.padeladmin.padeladmin.entity.Complex;
import com.padeladmin.padeladmin.entity.Court;
import com.padeladmin.padeladmin.enums.BookingSource;
import com.padeladmin.padeladmin.exception.BusinessException;
import com.padeladmin.padeladmin.exception.ResourceNotFoundException;
import com.padeladmin.padeladmin.repository.ComplexRepository;
import com.padeladmin.padeladmin.repository.CourtRepository;
import com.padeladmin.padeladmin.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Slf4j
public class PublicBookingController {

    private final BookingService bookingService;
    private final ComplexRepository complexRepository;
    private final CourtRepository courtRepository;

    /**
     * GET /api/public/complexes
     * Obtener lista de complejos activos (sin autenticación)
     */
    @GetMapping("/complexes")
    public ResponseEntity<List<ComplexResponseDto>> getComplexes() {
        List<Complex> complexes = complexRepository.findAll();

        List<ComplexResponseDto> dtos = complexes.stream()
            .map(c -> ComplexResponseDto.builder()
                .id(c.getId())
                .name(c.getName())
                .address(c.getAddress())
                .phone(c.getPhone())
                .courts(c.getCourts().stream()
                    .filter(Court::isActive)
                    .map(ct -> CourtResponseDto.builder()
                        .id(ct.getId())
                        .name(ct.getName())
                        .active(ct.isActive())
                        .slotDurationMinutes(ct.getSlotDurationMinutes() != null
                            ? ct.getSlotDurationMinutes()
                            : 90)
                        .build())
                    .collect(Collectors.toList()))
                .build())
            .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * GET /api/public/courts/{courtId}/slots?date=YYYY-MM-DD
     * Obtener slots disponibles para una cancha en una fecha
     */
    @GetMapping("/courts/{courtId}/slots")
    public ResponseEntity<?> getAvailableSlots(
        @PathVariable Long courtId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        try {
            List<AvailableSlotDto> slots = bookingService.getAvailableSlots(courtId, date);

            Court court = courtRepository.findById(courtId)
                .orElseThrow(() -> new ResourceNotFoundException("Court", courtId));

            Map<String, Object> response = new HashMap<>();
            response.put("courtId", courtId);
            response.put("courtName", court.getName());
            response.put("date", date);
            response.put("slots", slots);

            return ResponseEntity.ok(response);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * POST /api/public/bookings
     * Crear una nueva reserva (sin autenticación)
     * Body: CourtBookingRequestDto
     */
    @PostMapping("/bookings")
    public ResponseEntity<?> createPublicBooking(@Valid @RequestBody CourtBookingRequestDto requestDto) {
        try {
            CourtBookingResponseDto response = bookingService.createBooking(
                requestDto,
                BookingSource.PUBLIC
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (BusinessException e) {
            return ResponseEntity.status(400).body(Map.of("message", e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating public booking", e);
            return ResponseEntity.status(500).body(Map.of("message", "Error interno del servidor"));
        }
    }
}
