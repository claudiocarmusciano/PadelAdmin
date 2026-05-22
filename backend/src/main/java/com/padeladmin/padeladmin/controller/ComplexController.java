package com.padeladmin.padeladmin.controller;

import com.padeladmin.padeladmin.dto.complex.ComplexRequestDto;
import com.padeladmin.padeladmin.dto.complex.ComplexResponseDto;
import com.padeladmin.padeladmin.dto.complex.CourtRequestDto;
import com.padeladmin.padeladmin.dto.complex.CourtResponseDto;
import com.padeladmin.padeladmin.service.ComplexService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/complexes")
@RequiredArgsConstructor
public class ComplexController {

    private final ComplexService complexService;

    @GetMapping
    public ResponseEntity<List<ComplexResponseDto>> findAll() {
        return ResponseEntity.ok(complexService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ComplexResponseDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(complexService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ComplexResponseDto> create(@Valid @RequestBody ComplexRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complexService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ComplexResponseDto> update(@PathVariable Long id,
                                                     @Valid @RequestBody ComplexRequestDto dto) {
        return ResponseEntity.ok(complexService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        complexService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Canchas ───────────────────────────────────────────────────────────────

    @PostMapping("/{complexId}/courts")
    public ResponseEntity<CourtResponseDto> addCourt(@PathVariable Long complexId,
                                                     @Valid @RequestBody CourtRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complexService.addCourt(complexId, dto));
    }

    @PutMapping("/{complexId}/courts/{courtId}")
    public ResponseEntity<CourtResponseDto> updateCourt(@PathVariable Long complexId,
                                                        @PathVariable Long courtId,
                                                        @Valid @RequestBody CourtRequestDto dto) {
        return ResponseEntity.ok(complexService.updateCourt(complexId, courtId, dto));
    }

    @PatchMapping("/{complexId}/courts/{courtId}/toggle")
    public ResponseEntity<Void> toggleCourtActive(@PathVariable Long complexId,
                                                   @PathVariable Long courtId) {
        complexService.toggleCourtActive(complexId, courtId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{complexId}/courts/{courtId}")
    public ResponseEntity<Void> deleteCourt(@PathVariable Long complexId,
                                            @PathVariable Long courtId) {
        complexService.deleteCourt(complexId, courtId);
        return ResponseEntity.noContent().build();
    }
}
