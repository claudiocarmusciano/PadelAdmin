package com.padeladmin.padeladmin.controller;

import com.padeladmin.padeladmin.dto.complex.CourtAvailabilityRequestDto;
import com.padeladmin.padeladmin.dto.complex.CourtAvailabilityResponseDto;
import com.padeladmin.padeladmin.service.CourtAvailabilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courts/{courtId}/availability")
@RequiredArgsConstructor
public class CourtAvailabilityController {

    private final CourtAvailabilityService availabilityService;

    @GetMapping
    public ResponseEntity<List<CourtAvailabilityResponseDto>> findAll(@PathVariable Long courtId) {
        return ResponseEntity.ok(availabilityService.findByCourt(courtId));
    }

    // Crea o actualiza la disponibilidad para un día (upsert por día)
    @PostMapping
    public ResponseEntity<CourtAvailabilityResponseDto> save(
            @PathVariable Long courtId,
            @Valid @RequestBody CourtAvailabilityRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(availabilityService.save(courtId, dto));
    }

    @DeleteMapping("/{availabilityId}")
    public ResponseEntity<Void> delete(@PathVariable Long courtId,
                                       @PathVariable Long availabilityId) {
        availabilityService.delete(courtId, availabilityId);
        return ResponseEntity.noContent().build();
    }

    // Copia los horarios de esta cancha a las demás canchas activas del complejo
    @PostMapping("/copy-to-complex")
    public ResponseEntity<Map<String, Object>> copyToComplex(@PathVariable Long courtId) {
        int updated = availabilityService.copyToComplex(courtId);
        return ResponseEntity.ok(Map.of("courtsUpdated", updated));
    }
}
