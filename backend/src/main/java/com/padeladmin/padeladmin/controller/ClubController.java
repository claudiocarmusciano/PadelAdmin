package com.padeladmin.padeladmin.controller;

import com.padeladmin.padeladmin.dto.club.ClubRequestDto;
import com.padeladmin.padeladmin.dto.club.ClubResponseDto;
import com.padeladmin.padeladmin.service.ClubService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Gestión de clubes (tenants). Solo super-admin (ver SecurityConfig: /api/clubs/** → ADMIN). */
@RestController
@RequestMapping("/api/clubs")
@RequiredArgsConstructor
public class ClubController {

    private final ClubService clubService;

    @GetMapping
    public ResponseEntity<List<ClubResponseDto>> findAll() {
        return ResponseEntity.ok(clubService.findAll());
    }

    @PostMapping
    public ResponseEntity<ClubResponseDto> create(@Valid @RequestBody ClubRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clubService.create(dto));
    }
}
