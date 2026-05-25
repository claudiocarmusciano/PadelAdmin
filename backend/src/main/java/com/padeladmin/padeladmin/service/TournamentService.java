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
import com.padeladmin.padeladmin.repository.MatchRepository;
import com.padeladmin.padeladmin.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final CategoryRepository categoryRepository;
    private final ComplexRepository complexRepository;
    private final MatchRepository matchRepository;

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
                .status(TournamentStatus.DRAFT)   // siempre inicia en borrador
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

        if (newStatus == TournamentStatus.ACTIVE) {
            // ACTIVE solo se puede alcanzar automáticamente al generar el fixture
            throw new BusinessException(
                    "El torneo se activa automáticamente al generar el fixture una vez llegada la fecha de inicio");
        }
        if (newStatus == TournamentStatus.COMPLETED && tournament.getStatus() != TournamentStatus.ACTIVE) {
            throw new BusinessException("Solo se puede finalizar un torneo que esté Activo");
        }

        tournament.setStatus(newStatus);
        return toDto(tournamentRepository.save(tournament));
    }

    /** Llamado internamente por FixtureService al generar el fixture. */
    @Transactional
    public void activateIfStarted(Long tournamentId) {
        Tournament tournament = getOrThrow(tournamentId);
        if (tournament.getStatus() == TournamentStatus.DRAFT
                && !tournament.getStartDate().isAfter(java.time.LocalDate.now())) {
            tournament.setStatus(TournamentStatus.ACTIVE);
            tournamentRepository.save(tournament);
        }
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
                .fixtureGenerated(matchRepository.existsByTournamentId(t.getId()))
                .zoneDays(new java.util.ArrayList<>(t.getZoneDays()))
                .createdAt(t.getCreatedAt())
                .build();
    }

    @Transactional
    public TournamentResponseDto setZoneDays(Long tournamentId, List<Integer> days) {
        Tournament t = getOrThrow(tournamentId);
        t.getZoneDays().clear();
        if (days != null) t.getZoneDays().addAll(days);
        return toDto(tournamentRepository.save(t));
    }
}
