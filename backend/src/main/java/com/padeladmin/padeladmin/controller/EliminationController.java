package com.padeladmin.padeladmin.controller;

import com.padeladmin.padeladmin.dto.elimination.EliminationBracketDto;
import com.padeladmin.padeladmin.service.EliminationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tournaments/{tournamentId}/elimination")
@RequiredArgsConstructor
public class EliminationController {

    private final EliminationService eliminationService;

    // Generar el bracket eliminatorio a partir de los clasificados de zona
    @PostMapping("/generate")
    public ResponseEntity<EliminationBracketDto> generate(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(eliminationService.generateBracket(tournamentId));
    }

    // Ver el bracket actual
    @GetMapping
    public ResponseEntity<EliminationBracketDto> getBracket(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(eliminationService.getBracket(tournamentId));
    }
}
