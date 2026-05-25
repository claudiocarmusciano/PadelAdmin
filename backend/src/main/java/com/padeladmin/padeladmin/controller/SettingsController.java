package com.padeladmin.padeladmin.controller;

import com.padeladmin.padeladmin.dto.settings.GlobalSettingsDto;
import com.padeladmin.padeladmin.dto.settings.PointConfigDto;
import com.padeladmin.padeladmin.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

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
