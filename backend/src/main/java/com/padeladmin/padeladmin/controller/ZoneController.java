package com.padeladmin.padeladmin.controller;

import com.padeladmin.padeladmin.dto.zone.MovePairRequestDto;
import com.padeladmin.padeladmin.dto.zone.SwapPairsRequestDto;
import com.padeladmin.padeladmin.dto.zone.ZoneResponseDto;
import com.padeladmin.padeladmin.service.ZoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tournaments/{tournamentId}/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneService zoneService;

    // Generar zonas automáticamente con el patrón snake
    @PostMapping("/generate")
    public ResponseEntity<List<ZoneResponseDto>> generate(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(zoneService.generateZones(tournamentId));
    }

    // Listar zonas del torneo
    @GetMapping
    public ResponseEntity<List<ZoneResponseDto>> findAll(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(zoneService.findByTournament(tournamentId));
    }

    // Obtener una zona específica
    @GetMapping("/{zoneId}")
    public ResponseEntity<ZoneResponseDto> findById(@PathVariable Long tournamentId,
                                                    @PathVariable Long zoneId) {
        return ResponseEntity.ok(zoneService.findById(tournamentId, zoneId));
    }

    // Mover una pareja a otra zona (ajuste manual, requiere espacio)
    @PatchMapping("/pairs/{pairId}/move")
    public ResponseEntity<List<ZoneResponseDto>> movePair(@PathVariable Long tournamentId,
                                                          @PathVariable Long pairId,
                                                          @Valid @RequestBody MovePairRequestDto dto) {
        return ResponseEntity.ok(zoneService.movePair(tournamentId, pairId, dto));
    }

    // Intercambiar dos parejas entre zonas
    @PatchMapping("/pairs/{pairId}/swap")
    public ResponseEntity<List<ZoneResponseDto>> swapPairs(@PathVariable Long tournamentId,
                                                           @PathVariable Long pairId,
                                                           @Valid @RequestBody SwapPairsRequestDto dto) {
        return ResponseEntity.ok(zoneService.swapPairs(tournamentId, pairId, dto));
    }
}
