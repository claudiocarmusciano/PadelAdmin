package com.padeladmin.padeladmin.service;

import com.padeladmin.padeladmin.dto.tournament.TournamentRequestDto;
import com.padeladmin.padeladmin.dto.tournament.TournamentResponseDto;
import com.padeladmin.padeladmin.entity.Category;
import com.padeladmin.padeladmin.entity.Complex;
import com.padeladmin.padeladmin.entity.Tournament;
import com.padeladmin.padeladmin.enums.TournamentStatus;
import com.padeladmin.padeladmin.exception.BusinessException;
import com.padeladmin.padeladmin.exception.ResourceNotFoundException;
import com.padeladmin.padeladmin.repository.CategoryRepository;
import com.padeladmin.padeladmin.repository.ComplexRepository;
import com.padeladmin.padeladmin.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final CategoryRepository categoryRepository;
    private final ComplexRepository complexRepository;

    public List<TournamentResponseDto> findAll() {
        return tournamentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    public List<TournamentResponseDto> findByStatus(TournamentStatus status) {
        return tournamentRepository.findByStatus(status).stream()
                .map(this::toDto)
                .toList();
    }

    public TournamentResponseDto findById(Long id) {
        return toDto(getOrThrow(id));
    }

    @Transactional
    public TournamentResponseDto create(TournamentRequestDto dto) {
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BusinessException("La fecha de fin no puede ser anterior a la de inicio");
        }

        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría", dto.getCategoryId()));

        Complex complex = null;
        if (dto.getComplexId() != null) {
            complex = complexRepository.findById(dto.getComplexId())
                    .orElseThrow(() -> new ResourceNotFoundException("Complejo", dto.getComplexId()));
        }

        Tournament tournament = Tournament.builder()
                .name(dto.getName())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .category(category)
                .complex(complex)
                .matchDurationMinutes(dto.getMatchDurationMinutes())
                .minIntervalMinutes(dto.getMinIntervalMinutes())
                .build();

        return toDto(tournamentRepository.save(tournament));
    }

    @Transactional
    public TournamentResponseDto update(Long id, TournamentRequestDto dto) {
        Tournament tournament = getOrThrow(id);

        if (tournament.getStatus() != TournamentStatus.DRAFT) {
            throw new BusinessException("Solo se pueden editar torneos en estado DRAFT");
        }
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BusinessException("La fecha de fin no puede ser anterior a la de inicio");
        }

        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría", dto.getCategoryId()));

        Complex complex = null;
        if (dto.getComplexId() != null) {
            complex = complexRepository.findById(dto.getComplexId())
                    .orElseThrow(() -> new ResourceNotFoundException("Complejo", dto.getComplexId()));
        }

        tournament.setName(dto.getName());
        tournament.setStartDate(dto.getStartDate());
        tournament.setEndDate(dto.getEndDate());
        tournament.setCategory(category);
        tournament.setComplex(complex);
        tournament.setMatchDurationMinutes(dto.getMatchDurationMinutes());
        tournament.setMinIntervalMinutes(dto.getMinIntervalMinutes());

        return toDto(tournamentRepository.save(tournament));
    }

    @Transactional
    public TournamentResponseDto updateStatus(Long id, TournamentStatus newStatus) {
        Tournament tournament = getOrThrow(id);
        tournament.setStatus(newStatus);
        return toDto(tournamentRepository.save(tournament));
    }

    @Transactional
    public void delete(Long id) {
        Tournament tournament = getOrThrow(id);
        if (tournament.getStatus() != TournamentStatus.DRAFT) {
            throw new BusinessException("Solo se pueden eliminar torneos en estado DRAFT");
        }
        tournamentRepository.deleteById(id);
    }

    private Tournament getOrThrow(Long id) {
        return tournamentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", id));
    }

    private TournamentResponseDto toDto(Tournament t) {
        return TournamentResponseDto.builder()
                .id(t.getId())
                .name(t.getName())
                .startDate(t.getStartDate())
                .endDate(t.getEndDate())
                .categoryId(t.getCategory().getId())
                .categoryName(t.getCategory().getName())
                .complexId(t.getComplex() != null ? t.getComplex().getId() : null)
                .complexName(t.getComplex() != null ? t.getComplex().getName() : null)
                .matchDurationMinutes(t.getMatchDurationMinutes())
                .minIntervalMinutes(t.getMinIntervalMinutes())
                .status(t.getStatus())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
