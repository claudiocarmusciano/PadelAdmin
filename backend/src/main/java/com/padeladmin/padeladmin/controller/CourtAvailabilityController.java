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
}
