package com.padeladmin.padeladmin.controller;

import com.padeladmin.padeladmin.dto.match.MatchResultRequestDto;
import com.padeladmin.padeladmin.dto.match.MatchResultResponseDto;
import com.padeladmin.padeladmin.dto.match.ZoneStandingDto;
import com.padeladmin.padeladmin.service.MatchResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MatchResultController {

    private final MatchResultService matchResultService;

    // Registrar resultado de un partido
    @PostMapping("/api/matches/{matchId}/result")
    public ResponseEntity<MatchResultResponseDto> recordResult(
            @PathVariable Long matchId,
            @Valid @RequestBody MatchResultRequestDto dto) {
        return ResponseEntity.ok(matchResultService.recordResult(matchId, dto));
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
}
