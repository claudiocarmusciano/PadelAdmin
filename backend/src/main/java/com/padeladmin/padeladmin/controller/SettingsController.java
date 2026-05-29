package com.padeladmin.padeladmin.controller;

import com.padeladmin.padeladmin.dto.settings.GlobalSettingsDto;
import com.padeladmin.padeladmin.dto.settings.PointConfigDto;
import com.padeladmin.padeladmin.service.SettingsService;
import com.padeladmin.padeladmin.service.TournamentPointsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final TournamentPointsService tournamentPointsService;

    // ── Puntos por etapa ──────────────────────────────────────────────────────

    @GetMapping("/points")
    public ResponseEntity<List<PointConfigDto>> getPoints() {
        return ResponseEntity.ok(settingsService.getPointConfigs());
    }

    @PutMapping("/points")
    public ResponseEntity<List<PointConfigDto>> updatePoints(
            @Valid @RequestBody List<PointConfigDto> dtos) {
        return ResponseEntity.ok(settingsService.updatePointConfigs(dtos));
    }

    /**
     * Limpieza de puntos para arrancar nueva temporada/semestre.
     * Pone en 0 todos los puntos vigentes de los jugadores. El historial de
     * otorgamientos por torneo se conserva.
     */
    @PostMapping("/points/reset")
    public ResponseEntity<Map<String, Object>> resetPoints() {
        int reset = tournamentPointsService.resetAllPoints();
        return ResponseEntity.ok(Map.of("reset", reset));
    }

    // ── Configuración general ─────────────────────────────────────────────────

    @GetMapping("/general")
    public ResponseEntity<GlobalSettingsDto> getGeneral() {
        return ResponseEntity.ok(settingsService.getGlobalSettings());
    }

    @PutMapping("/general")
    public ResponseEntity<GlobalSettingsDto> updateGeneral(
            @Valid @RequestBody GlobalSettingsDto dto) {
        return ResponseEntity.ok(settingsService.updateGlobalSettings(dto));
    }
}
