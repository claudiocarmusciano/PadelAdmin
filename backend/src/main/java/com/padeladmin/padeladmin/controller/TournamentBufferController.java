package com.padeladmin.padeladmin.controller;

import com.padeladmin.padeladmin.dto.tournament.TournamentBufferRequestDto;
import com.padeladmin.padeladmin.dto.tournament.TournamentBufferResponseDto;
import com.padeladmin.padeladmin.service.TournamentBufferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tournaments/{tournamentId}/buffers")
@RequiredArgsConstructor
public class TournamentBufferController {

    private final TournamentBufferService bufferService;

    @GetMapping
    public ResponseEntity<List<TournamentBufferResponseDto>> findAll(
            @PathVariable Long tournamentId) {
        return ResponseEntity.ok(bufferService.findByTournament(tournamentId));
    }

    @PostMapping
    public ResponseEntity<TournamentBufferResponseDto> add(
            @PathVariable Long tournamentId,
            @Valid @RequestBody TournamentBufferRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bufferService.add(tournamentId, dto));
    }

    @DeleteMapping("/{bufferId}")
    public ResponseEntity<Void> delete(@PathVariable Long tournamentId,
                                       @PathVariable Long bufferId) {
        bufferService.delete(tournamentId, bufferId);
        return ResponseEntity.noContent().build();
    }
}
