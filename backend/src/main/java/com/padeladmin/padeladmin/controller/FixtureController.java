package com.padeladmin.padeladmin.controller;

import com.padeladmin.padeladmin.dto.fixture.FixtureResponseDto;
import com.padeladmin.padeladmin.service.FixtureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tournaments/{tournamentId}/fixture")
@RequiredArgsConstructor
public class FixtureController {

    private final FixtureService fixtureService;

    // Genera partidos y los programa automáticamente
    @PostMapping("/generate")
    public ResponseEntity<FixtureResponseDto> generate(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(fixtureService.generateFixture(tournamentId));
    }

    // Ver el fixture actual del torneo
    @GetMapping
    public ResponseEntity<FixtureResponseDto> getFixture(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(fixtureService.getFixture(tournamentId));
    }

    // Programar solo los partidos que están en estado PENDING (ej: ronda 2 de zona de 4)
    @PostMapping("/schedule-pending")
    public ResponseEntity<FixtureResponseDto> schedulePending(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(fixtureService.schedulePendingMatches(tournamentId));
    }
}
