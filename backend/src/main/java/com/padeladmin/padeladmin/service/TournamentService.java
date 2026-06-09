package com.padeladmin.padeladmin.service;

import com.padeladmin.padeladmin.dto.tournament.TournamentRequestDto;
import com.padeladmin.padeladmin.dto.tournament.TournamentResponseDto;
import com.padeladmin.padeladmin.entity.Category;
import com.padeladmin.padeladmin.entity.Complex;
import com.padeladmin.padeladmin.entity.Tournament;
import com.padeladmin.padeladmin.enums.TournamentStatus;
import com.padeladmin.padeladmin.exception.BusinessException;
import com.padeladmin.padeladmin.exception.ResourceNotFoundException;
import com.padeladmin.padeladmin.repository.AvailabilityWindowRepository;
import com.padeladmin.padeladmin.repository.CategoryRepository;
import com.padeladmin.padeladmin.repository.ClubRepository;
import com.padeladmin.padeladmin.repository.ComplexRepository;
import com.padeladmin.padeladmin.repository.MatchRepository;
import com.padeladmin.padeladmin.repository.TournamentRepository;
import com.padeladmin.padeladmin.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final CategoryRepository categoryRepository;
    private final ComplexRepository complexRepository;
    private final MatchRepository matchRepository;
    private final AvailabilityWindowRepository availabilityWindowRepository;
    private final TournamentPointsService tournamentPointsService;
    private final JdbcTemplate jdbcTemplate;
    private final TenantContext tenantContext;
    private final ClubRepository clubRepository;

    public List<TournamentResponseDto> findAll() {
        return tournamentRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(this::visibleForCurrentUser)
                .map(this::toDto)
                .toList();
    }

    public List<TournamentResponseDto> findByStatus(TournamentStatus status) {
        return tournamentRepository.findByStatus(status).stream()
                .filter(this::visibleForCurrentUser)
                .map(this::toDto)
                .toList();
    }

    private boolean visibleForCurrentUser(Tournament t) {
        return tenantContext.canAccessClub(t.getClub() != null ? t.getClub().getId() : null);
    }

    public TournamentResponseDto findById(Long id) {
        return toDto(getOrThrow(id));
    }

    @Transactional
    public TournamentResponseDto create(TournamentRequestDto dto) {
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BusinessException("La fecha de fin no puede ser anterior a la de inicio");
        }

        String name = dto.getName() == null ? "" : dto.getName().trim();
        if (nameTakenInClub(name, null)) {
            throw new BusinessException("Ya existe un torneo con el nombre \"" + name + "\". Elegí otro nombre.");
        }

        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría", dto.getCategoryId()));
        requireSameClub(category.getClub() != null ? category.getClub().getId() : null, "Categoría", dto.getCategoryId());

        Complex complex = null;
        if (dto.getComplexId() != null) {
            complex = complexRepository.findById(dto.getComplexId())
                    .orElseThrow(() -> new ResourceNotFoundException("Complejo", dto.getComplexId()));
            requireSameClub(complex.getClub() != null ? complex.getClub().getId() : null, "Complejo", dto.getComplexId());
        }

        Tournament tournament = Tournament.builder()
                .name(name)
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .category(category)
                .complex(complex)
                .matchDurationMinutes(dto.getMatchDurationMinutes())
                .minIntervalMinutes(dto.getMinIntervalMinutes())
                .status(TournamentStatus.DRAFT)   // siempre inicia en borrador
                .build();
        // Los torneos creados por un usuario CLUB nacen dentro de su club.
        tenantContext.restrictedClubId().ifPresent(clubId ->
                tournament.setClub(clubRepository.getReferenceById(clubId)));

        return toDto(tournamentRepository.save(tournament));
    }

    @Transactional
    public TournamentResponseDto update(Long id, TournamentRequestDto dto) {
        Tournament tournament = getOrThrow(id);

        // Permitir edición en DRAFT sin restricciones, o en otros estados si no hay resultados cargados
        if (tournament.getStatus() != TournamentStatus.DRAFT) {
            boolean hasPlayedMatches = matchRepository.existsPlayedMatchesByTournamentId(id);
            if (hasPlayedMatches) {
                throw new BusinessException("No se pueden editar torneos con resultados cargados");
            }
        }

        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BusinessException("La fecha de fin no puede ser anterior a la de inicio");
        }

        String name = dto.getName() == null ? "" : dto.getName().trim();
        if (nameTakenInClub(name, id)) {
            throw new BusinessException("Ya existe otro torneo con el nombre \"" + name + "\". Elegí otro nombre.");
        }

        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría", dto.getCategoryId()));
        requireSameClub(category.getClub() != null ? category.getClub().getId() : null, "Categoría", dto.getCategoryId());

        Complex complex = null;
        if (dto.getComplexId() != null) {
            complex = complexRepository.findById(dto.getComplexId())
                    .orElseThrow(() -> new ResourceNotFoundException("Complejo", dto.getComplexId()));
            requireSameClub(complex.getClub() != null ? complex.getClub().getId() : null, "Complejo", dto.getComplexId());
        }

        tournament.setName(name);
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
        boolean isFinalizing = newStatus == TournamentStatus.COMPLETED
                && tournament.getStatus() == TournamentStatus.ACTIVE;

        if (newStatus == TournamentStatus.COMPLETED && tournament.getStatus() != TournamentStatus.ACTIVE) {
            throw new BusinessException("Solo se puede finalizar un torneo que esté Activo");
        }

        tournament.setStatus(newStatus);
        Tournament saved = tournamentRepository.save(tournament);

        // Al finalizar: otorgar puntos de ranking a los jugadores (mejor instancia, idempotente)
        if (isFinalizing) {
            tournamentPointsService.awardPointsForTournament(saved);
        }

        return toDto(saved);
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

        // Borrado robusto via SQL nativo en orden de dependencias (hoja → raíz).
        // Evitamos depender de cascades JPA que pueden no estar configuradas correctamente
        // en todas las entidades hijas. Todo dentro de la misma transacción.
        log.info("Eliminando torneo {} \"{}\" y todas sus dependencias...", id, tournament.getName());

        // Nivel 4: sets de partidos jugados
        jdbcTemplate.update("""
            DELETE FROM match_sets
            WHERE match_result_id IN (
                SELECT id FROM match_results
                WHERE match_id IN (SELECT id FROM matches WHERE tournament_id = ?)
            )
            """, id);

        // Nivel 3: resultados de partidos
        jdbcTemplate.update("""
            DELETE FROM match_results
            WHERE match_id IN (SELECT id FROM matches WHERE tournament_id = ?)
            """, id);

        // Nivel 2: partidos
        jdbcTemplate.update("DELETE FROM matches WHERE tournament_id = ?", id);

        // Nivel 2: restricciones / preferencias horarias por pareja
        jdbcTemplate.update("""
            DELETE FROM pair_schedule_constraints
            WHERE pair_id IN (SELECT id FROM pairs WHERE tournament_id = ?)
            """, id);

        // Nivel 2: jugadores en cada pareja
        jdbcTemplate.update("""
            DELETE FROM pair_players
            WHERE pair_id IN (SELECT id FROM pairs WHERE tournament_id = ?)
            """, id);

        // Nivel 2: parejas en cada zona
        jdbcTemplate.update("""
            DELETE FROM zone_pairs
            WHERE zone_id IN (SELECT id FROM zones WHERE tournament_id = ?)
            """, id);

        // Nivel 1: parejas y zonas del torneo
        jdbcTemplate.update("DELETE FROM pairs WHERE tournament_id = ?", id);
        jdbcTemplate.update("DELETE FROM zones WHERE tournament_id = ?", id);

        // Nivel 1: buffers y días de zona
        jdbcTemplate.update("DELETE FROM tournament_buffers WHERE tournament_id = ?", id);
        jdbcTemplate.update("DELETE FROM tournament_zone_days WHERE tournament_id = ?", id);

        // Nivel 1: ventanas de disponibilidad
        jdbcTemplate.update("DELETE FROM availability_windows WHERE tournament_id = ?", id);

        // Finalmente, el torneo
        jdbcTemplate.update("DELETE FROM tournaments WHERE id = ?", id);

        log.info("✓ Torneo {} eliminado", id);
    }

    private Tournament getOrThrow(Long id) {
        return tournamentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", id));
    }

    /**
     * Unicidad de nombre dentro del mismo club (los clubes no compiten entre sí por nombres).
     * Para usuarios globales (ADMIN) el chequeo sigue siendo global, como hasta ahora.
     */
    private boolean nameTakenInClub(String name, Long excludeId) {
        Long clubId = tenantContext.restrictedClubId().orElse(null);
        return tournamentRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(t -> excludeId == null || !t.getId().equals(excludeId))
                .filter(t -> clubId == null || (t.getClub() != null && clubId.equals(t.getClub().getId())))
                .anyMatch(t -> t.getName().equalsIgnoreCase(name));
    }

    /** Un usuario CLUB solo puede referenciar categorías/complejos de su propio club. */
    private void requireSameClub(Long resourceClubId, String entity, Long resourceId) {
        if (!tenantContext.canAccessClub(resourceClubId)) {
            throw new ResourceNotFoundException(entity, resourceId);
        }
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
                .hasResults(matchRepository.existsPlayedMatchesByTournamentId(t.getId()))
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
