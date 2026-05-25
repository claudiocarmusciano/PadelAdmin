package com.padeladmin.padeladmin.controller;

import com.padeladmin.padeladmin.dto.tournament.TournamentRequestDto;
import com.padeladmin.padeladmin.dto.tournament.TournamentResponseDto;
import com.padeladmin.padeladmin.enums.TournamentStatus;
import com.padeladmin.padeladmin.service.TournamentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tournaments")
@RequiredArgsConstructor
public class TournamentController {

    private final TournamentService tournamentService;

    @GetMapping
    public ResponseEntity<List<TournamentResponseDto>> findAll(
            @RequestParam(required = false) TournamentStatus status) {
        if (status != null) {
            return ResponseEntity.ok(tournamentService.findByStatus(status));
        }
        return ResponseEntity.ok(tournamentService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TournamentResponseDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(tournamentService.findById(id));
    }

    @PostMapping
    public ResponseEntity<TournamentResponseDto> create(@Valid @RequestBody TournamentRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tournamentService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TournamentResponseDto> update(@PathVariable Long id,
                                                        @Valid @RequestBody TournamentRequestDto dto) {
        return ResponseEntity.ok(tournamentService.update(id, dto));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TournamentResponseDto> updateStatus(@PathVariable Long id,
                                                              @RequestParam TournamentStatus status) {
        return ResponseEntity.ok(tournamentService.updateStatus(id, status));
    }

    @PutMapping("/{id}/zone-days")
    public ResponseEntity<TournamentResponseDto> setZoneDays(@PathVariable Long id,
                                                              @RequestBody List<Integer> days) {
        return ResponseEntity.ok(tournamentService.setZoneDays(id, days));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tournamentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
