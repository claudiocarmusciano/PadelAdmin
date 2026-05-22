package com.padeladmin.padeladmin.controller;

import com.padeladmin.padeladmin.dto.pair.*;
import com.padeladmin.padeladmin.service.PairService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tournaments/{tournamentId}/pairs")
@RequiredArgsConstructor
public class PairController {

    private final PairService pairService;

    @GetMapping
    public ResponseEntity<List<PairResponseDto>> findAll(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(pairService.findByTournament(tournamentId));
    }

    @GetMapping("/{pairId}")
    public ResponseEntity<PairResponseDto> findById(@PathVariable Long tournamentId,
                                                    @PathVariable Long pairId) {
        return ResponseEntity.ok(pairService.findById(tournamentId, pairId));
    }

    @PostMapping
    public ResponseEntity<PairResponseDto> create(@PathVariable Long tournamentId,
                                                  @Valid @RequestBody PairRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pairService.create(tournamentId, dto));
    }

    @DeleteMapping("/{pairId}")
    public ResponseEntity<Void> delete(@PathVariable Long tournamentId,
                                       @PathVariable Long pairId) {
        pairService.delete(tournamentId, pairId);
        return ResponseEntity.noContent().build();
    }

    // ── Preferencias y restricciones horarias ─────────────────────────────────

    @PostMapping("/{pairId}/constraints")
    public ResponseEntity<PairScheduleConstraintResponseDto> addConstraint(
            @PathVariable Long tournamentId,
            @PathVariable Long pairId,
            @Valid @RequestBody PairScheduleConstraintRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pairService.addConstraint(tournamentId, pairId, dto));
    }

    @DeleteMapping("/{pairId}/constraints/{constraintId}")
    public ResponseEntity<Void> removeConstraint(@PathVariable Long tournamentId,
                                                 @PathVariable Long pairId,
                                                 @PathVariable Long constraintId) {
        pairService.removeConstraint(tournamentId, pairId, constraintId);
        return ResponseEntity.noContent().build();
    }
}
