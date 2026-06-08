package com.padeladmin.padeladmin.controller;

import com.padeladmin.padeladmin.dto.fixture.MatchPlacementDto;
import com.padeladmin.padeladmin.dto.fixture.MatchResponseDto;
import com.padeladmin.padeladmin.dto.match.MatchResultRequestDto;
import com.padeladmin.padeladmin.dto.match.MatchResultResponseDto;
import com.padeladmin.padeladmin.dto.match.ZoneStandingDto;
import com.padeladmin.padeladmin.service.FixtureService;
import com.padeladmin.padeladmin.service.MatchResultService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class MatchResultController {

    private final MatchResultService matchResultService;
    private final FixtureService fixtureService;

    // Registrar resultado de un partido
    @PostMapping("/api/matches/{matchId}/result")
    public ResponseEntity<MatchResultResponseDto> recordResult(
            @PathVariable Long matchId,
            @Valid @RequestBody MatchResultRequestDto dto) {
        return ResponseEntity.ok(matchResultService.recordResult(matchId, dto));
    }

    // Editar resultado de un partido ya jugado
    @PutMapping("/api/matches/{matchId}/result")
    public ResponseEntity<MatchResultResponseDto> updateResult(
            @PathVariable Long matchId,
            @Valid @RequestBody MatchResultRequestDto dto) {
        return ResponseEntity.ok(matchResultService.updateResult(matchId, dto));
    }

    // Obtener resultado de un partido
    @GetMapping("/api/matches/{matchId}/result")
    public ResponseEntity<MatchResultResponseDto> getResult(@PathVariable Long matchId) {
        return ResponseEntity.ok(matchResultService.getResult(matchId));
    }

    // Tabla de posiciones de una zona
    @GetMapping("/api/zones/{zoneId}/standings")
    public ResponseEntity<List<ZoneStandingDto>> getStandings(@PathVariable Long zoneId) {
        return ResponseEntity.ok(matchResultService.getZoneStandings(zoneId));
    }

    // Cambiar cancha (y opcionalmente horario) de un partido
    @PatchMapping("/api/matches/{matchId}/court")
    public ResponseEntity<MatchResponseDto> updateCourt(
            @PathVariable Long matchId,
            @RequestBody CourtUpdateDto dto) {
        return ResponseEntity.ok(
                fixtureService.updateMatchCourt(matchId, dto.getCourtId(), dto.getScheduledStart()));
    }

    // Destinos posibles para mover un partido (verde/rojo según validez)
    @GetMapping("/api/matches/{matchId}/placements")
    public ResponseEntity<List<MatchPlacementDto>> getPlacements(@PathVariable Long matchId) {
        return ResponseEntity.ok(fixtureService.getPlacementsForMatch(matchId));
    }

    // Mover un partido a (cancha, horario) — valida el destino y rechaza si es inválido
    @PatchMapping("/api/matches/{matchId}/move")
    public ResponseEntity<MatchResponseDto> move(
            @PathVariable Long matchId,
            @RequestBody CourtUpdateDto dto) {
        return ResponseEntity.ok(
                fixtureService.moveMatch(matchId, dto.getCourtId(), dto.getScheduledStart()));
    }

    @Data
    static class CourtUpdateDto {
        private Long courtId;                 // null para quitar la cancha
        private LocalDateTime scheduledStart; // null = mantener el horario actual
    }
}
