package com.padeladmin.padeladmin.controller;

import com.padeladmin.padeladmin.dto.booking.CourtBookingRequestDto;
import com.padeladmin.padeladmin.dto.booking.CourtBookingResponseDto;
import com.padeladmin.padeladmin.entity.CourtBooking;
import com.padeladmin.padeladmin.enums.BookingSource;
import com.padeladmin.padeladmin.enums.BookingStatus;
import com.padeladmin.padeladmin.exception.BusinessException;
import com.padeladmin.padeladmin.exception.ResourceNotFoundException;
import com.padeladmin.padeladmin.repository.CourtBookingRepository;
import com.padeladmin.padeladmin.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Slf4j
public class BookingController {

    private final BookingService bookingService;
    private final CourtBookingRepository bookingRepository;

    /**
     * GET /api/bookings?complexId=X&date=YYYY-MM-DD
     * Obtener reservas (admin, requiere autenticación + ADMIN)
     *
     * Query params:
     *   - complexId (required)
     *   - date (required)
     */
    @GetMapping
    public ResponseEntity<?> getBookings(
        @RequestParam Long complexId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        try {
            List<CourtBookingResponseDto> bookings = bookingService
                .getBookingsByComplexAndDate(complexId, date);

            return ResponseEntity.ok(bookings);
        } catch (Exception e) {
            log.error("Error fetching bookings", e);
            return ResponseEntity.status(500).body(Map.of("message", "Error interno"));
        }
    }

    /**
     * POST /api/bookings
     * Crear reserva manual (admin)
     * Body: CourtBookingRequestDto
     */
    @PostMapping
    public ResponseEntity<?> createAdminBooking(@Valid @RequestBody CourtBookingRequestDto requestDto) {
        try {
            CourtBookingResponseDto response = bookingService.createBooking(
                requestDto,
                BookingSource.ADMIN
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (BusinessException e) {
            return ResponseEntity.status(400).body(Map.of("message", e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating admin booking", e);
            return ResponseEntity.status(500).body(Map.of("message", "Error interno del servidor"));
        }
    }

    /**
     * DELETE /api/bookings/{id}
     * Cancelar una reserva
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelBooking(@PathVariable Long id) {
        try {
            bookingService.cancelBooking(id);
            return ResponseEntity.ok(Map.of("message", "Reserva cancelada exitosamente"));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error cancelling booking", e);
            return ResponseEntity.status(500).body(Map.of("message", "Error interno del servidor"));
        }
    }

    /**
     * PATCH /api/bookings/{id}
     * Cambiar estado de reserva
     * Body: { "status": "CONFIRMED" | "CANCELLED" }
     */
    @PatchMapping("/{id}")
    public ResponseEntity<?> updateBookingStatus(
        @PathVariable Long id,
        @RequestBody Map<String, String> request) {

        try {
            CourtBooking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));

            String newStatus = request.get("status");
            if (newStatus == null || newStatus.isBlank()) {
                return ResponseEntity.status(400).body(Map.of("message", "El campo 'status' es requerido"));
            }

            try {
                booking.setStatus(BookingStatus.valueOf(newStatus));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(400).body(Map.of("message", "Estado inválido: " + newStatus));
            }

            bookingRepository.save(booking);
            return ResponseEntity.ok(Map.of("message", "Estado actualizado"));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating booking status", e);
            return ResponseEntity.status(500).body(Map.of("message", "Error interno del servidor"));
        }
    }
}
