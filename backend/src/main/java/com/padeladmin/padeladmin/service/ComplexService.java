package com.padeladmin.padeladmin.service;

import com.padeladmin.padeladmin.dto.complex.ComplexRequestDto;
import com.padeladmin.padeladmin.dto.complex.ComplexResponseDto;
import com.padeladmin.padeladmin.dto.complex.CourtRequestDto;
import com.padeladmin.padeladmin.dto.complex.CourtResponseDto;
import com.padeladmin.padeladmin.entity.Complex;
import com.padeladmin.padeladmin.entity.Court;
import com.padeladmin.padeladmin.exception.BusinessException;
import com.padeladmin.padeladmin.exception.ResourceNotFoundException;
import com.padeladmin.padeladmin.repository.ComplexRepository;
import com.padeladmin.padeladmin.repository.CourtRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComplexService {

    private final ComplexRepository complexRepository;
    private final CourtRepository courtRepository;

    public List<ComplexResponseDto> findAll() {
        return complexRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public ComplexResponseDto findById(Long id) {
        return toDto(getOrThrow(id));
    }

    @Transactional
    public ComplexResponseDto create(ComplexRequestDto dto) {
        Complex complex = Complex.builder()
                .name(dto.getName())
                .address(dto.getAddress())
                .phone(dto.getPhone())
                .build();
        return toDto(complexRepository.save(complex));
    }

    @Transactional
    public ComplexResponseDto update(Long id, ComplexRequestDto dto) {
        Complex complex = getOrThrow(id);
        complex.setName(dto.getName());
        complex.setAddress(dto.getAddress());
        complex.setPhone(dto.getPhone());
        return toDto(complexRepository.save(complex));
    }

    @Transactional
    public void delete(Long id) {
        if (!complexRepository.existsById(id)) {
            throw new ResourceNotFoundException("Complejo", id);
        }
        complexRepository.deleteById(id);
    }

    // ── Canchas ───────────────────────────────────────────────────────────────

    public List<CourtResponseDto> getCourts(Long complexId) {
        getOrThrow(complexId); // valida que el complejo existe
        return courtRepository.findByComplexIdOrderByNameAsc(complexId).stream()
                .map(this::toCourtDto)
                .toList();
    }

    @Transactional
    public CourtResponseDto addCourt(Long complexId, CourtRequestDto dto) {
        Complex complex = getOrThrow(complexId);
        if (courtRepository.existsByComplexIdAndNameIgnoreCase(complexId, dto.getName())) {
            throw new BusinessException("Ya existe una cancha con el nombre \"" + dto.getName() + "\" en este complejo");
        }
        Court court = Court.builder()
                .complex(complex)
                .name(dto.getName())
                .build();
        return toCourtDto(courtRepository.save(court));
    }

    @Transactional
    public CourtResponseDto updateCourt(Long complexId, Long courtId, CourtRequestDto dto) {
        Court court = getCourtOrThrow(complexId, courtId);
        court.setName(dto.getName());
        return toCourtDto(courtRepository.save(court));
    }

    @Transactional
    public void toggleCourtActive(Long complexId, Long courtId) {
        Court court = getCourtOrThrow(complexId, courtId);
        court.setActive(!court.isActive());
        courtRepository.save(court);
    }

    @Transactional
    public void deleteCourt(Long complexId, Long courtId) {
        Court court = getCourtOrThrow(complexId, courtId);
        courtRepository.delete(court);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Complex getOrThrow(Long id) {
        return complexRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Complejo", id));
    }

    private Court getCourtOrThrow(Long complexId, Long courtId) {
        Court court = courtRepository.findById(courtId)
                .orElseThrow(() -> new ResourceNotFoundException("Cancha", courtId));
        if (!court.getComplex().getId().equals(complexId)) {
            throw new BusinessException("La cancha no pertenece al complejo indicado");
        }
        return court;
    }

    private ComplexResponseDto toDto(Complex complex) {
        List<CourtResponseDto> courts = complex.getCourts().stream()
                .map(this::toCourtDto)
                .toList();
        return ComplexResponseDto.builder()
                .id(complex.getId())
                .name(complex.getName())
                .address(complex.getAddress())
                .phone(complex.getPhone())
                .courts(courts)
                .build();
    }

    private CourtResponseDto toCourtDto(Court court) {
        return CourtResponseDto.builder()
                .id(court.getId())
                .name(court.getName())
                .active(court.isActive())
                .build();
    }
}
