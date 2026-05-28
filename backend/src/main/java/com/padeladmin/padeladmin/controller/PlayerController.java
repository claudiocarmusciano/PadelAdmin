package com.padeladmin.padeladmin.controller;

import com.padeladmin.padeladmin.dto.player.PlayerCategoryPointsRequestDto;
import com.padeladmin.padeladmin.dto.player.PlayerCategoryPointsResponseDto;
import com.padeladmin.padeladmin.dto.player.PlayerRequestDto;
import com.padeladmin.padeladmin.dto.player.PlayerResponseDto;
import com.padeladmin.padeladmin.dto.player.PlayerStatsDto;
import com.padeladmin.padeladmin.dto.player.PlayerWithCategoriesDto;
import com.padeladmin.padeladmin.service.PlayerService;
import com.padeladmin.padeladmin.service.PlayerStatsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;
    private final PlayerStatsService playerStatsService;

    @GetMapping
    public ResponseEntity<List<PlayerResponseDto>> findAll(
            @RequestParam(required = false) String search) {
        if (search != null && !search.isBlank()) {
            return ResponseEntity.ok(playerService.search(search));
        }
        return ResponseEntity.ok(playerService.findAll());
    }

    @GetMapping("/with-categories")
    public ResponseEntity<List<PlayerWithCategoriesDto>> findAllWithCategories(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(playerService.findAllWithCategories(categoryId, search));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlayerResponseDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(playerService.findById(id));
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<PlayerStatsDto> getStats(@PathVariable Long id) {
        return ResponseEntity.ok(playerStatsService.computeStats(id));
    }

    @PostMapping
    public ResponseEntity<PlayerResponseDto> create(@Valid @RequestBody PlayerRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(playerService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlayerResponseDto> update(@PathVariable Long id,
                                                    @Valid @RequestBody PlayerRequestDto dto) {
        return ResponseEntity.ok(playerService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        playerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Puntos por categoría ──────────────────────────────────────────────────

    @GetMapping("/{id}/categories")
    public ResponseEntity<List<PlayerCategoryPointsResponseDto>> getPoints(@PathVariable Long id) {
        return ResponseEntity.ok(playerService.getPoints(id));
    }

    @PutMapping("/{id}/categories")
    public ResponseEntity<PlayerCategoryPointsResponseDto> upsertPoints(
            @PathVariable Long id,
            @Valid @RequestBody PlayerCategoryPointsRequestDto dto) {
        return ResponseEntity.ok(playerService.upsertPoints(id, dto));
    }

    @DeleteMapping("/{id}/categories/{categoryId}")
    public ResponseEntity<Void> deletePoints(@PathVariable Long id,
                                             @PathVariable Long categoryId) {
        playerService.deletePoints(id, categoryId);
        return ResponseEntity.noContent().build();
    }
}
