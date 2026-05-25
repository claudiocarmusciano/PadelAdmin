package com.padeladmin.padeladmin.service;

import com.padeladmin.padeladmin.dto.settings.GlobalSettingsDto;
import com.padeladmin.padeladmin.dto.settings.PointConfigDto;
import com.padeladmin.padeladmin.entity.GlobalSettings;
import com.padeladmin.padeladmin.entity.PointConfig;
import com.padeladmin.padeladmin.enums.TournamentStage;
import com.padeladmin.padeladmin.repository.GlobalSettingsRepository;
import com.padeladmin.padeladmin.repository.PointConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final PointConfigRepository pointConfigRepository;
    private final GlobalSettingsRepository globalSettingsRepository;

    // ── Point configs ─────────────────────────────────────────────────────────

    public List<PointConfigDto> getPointConfigs() {
        Map<TournamentStage, BigDecimal> existing = pointConfigRepository.findAll()
                .stream()
                .collect(Collectors.toMap(PointConfig::getStage, PointConfig::getPoints));

        // Devuelve todos los stages en orden definido por el enum, con 0 si aún no se configuró
        return Arrays.stream(TournamentStage.values())
                .map(stage -> new PointConfigDto(stage, existing.getOrDefault(stage, BigDecimal.ZERO)))
                .collect(Collectors.toList());
    }

    @Transactional
    public List<PointConfigDto> updatePointConfigs(List<PointConfigDto> dtos) {
        List<PointConfig> entities = dtos.stream()
                .map(dto -> new PointConfig(dto.getStage(), dto.getPoints()))
                .collect(Collectors.toList());
        pointConfigRepository.saveAll(entities);
        return getPointConfigs();
    }

    // ── Global settings ───────────────────────────────────────────────────────

    public GlobalSettingsDto getGlobalSettings() {
        GlobalSettings settings = globalSettingsRepository.findById(1L)
                .orElseGet(this::createDefaults);
        return toDto(settings);
    }

    @Transactional
    public GlobalSettingsDto updateGlobalSettings(GlobalSettingsDto dto) {
        GlobalSettings settings = globalSettingsRepository.findById(1L)
                .orElseGet(this::createDefaults);
        settings.setDefaultMatchDurationMinutes(dto.getDefaultMatchDurationMinutes());
        settings.setDefaultMinIntervalMinutes(dto.getDefaultMinIntervalMinutes());
        return toDto(globalSettingsRepository.save(settings));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private GlobalSettings createDefaults() {
        return globalSettingsRepository.save(new GlobalSettings(1L, 90, 60));
    }

    private GlobalSettingsDto toDto(GlobalSettings s) {
        return new GlobalSettingsDto(s.getDefaultMatchDurationMinutes(), s.getDefaultMinIntervalMinutes());
    }
}
