package com.padeladmin.padeladmin.service;

import com.padeladmin.padeladmin.dto.pair.*;
import com.padeladmin.padeladmin.entity.*;
import com.padeladmin.padeladmin.enums.MatchPhase;
import com.padeladmin.padeladmin.enums.MatchStatus;
import com.padeladmin.padeladmin.exception.BusinessException;
import com.padeladmin.padeladmin.exception.ResourceNotFoundException;
import com.padeladmin.padeladmin.repository.*;
import com.padeladmin.padeladmin.repository.CategoryRepository;
import com.padeladmin.padeladmin.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PairService {

    private final PairRepository pairRepository;
    private final PairPlayerRepository pairPlayerRepository;
    private final PairScheduleConstraintRepository constraintRepository;
    private final PlayerRepository playerRepository;
    private final TournamentRepository tournamentRepository;
    private final CategoryRepository categoryRepository;
    private final PlayerCategoryPointsRepository pointsRepository;
    private final MatchRepository matchRepository;
    private final ZoneRepository zoneRepository;

    private static final String[] DAY_NAMES = {
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"
    };

    public List<PairResponseDto> findByTournament(Long tournamentId) {
        getTournamentOrThrow(tournamentId);
        return pairRepository.findByTournamentIdOrderByTotalPointsDesc(tournamentId).stream()
                .map(this::toDto)
                .toList();
    }

    public PairResponseDto findById(Long tournamentId, Long pairId) {
        Pair pair = getPairOrThrow(tournamentId, pairId);
        return toDto(pair);
    }

    @Transactional
    public PairResponseDto create(Long tournamentId, PairRequestDto dto) {
        Tournament tournament = getTournamentOrThrow(tournamentId);

        PairPlayerEntryDto entry1 = dto.getPlayers().get(0);
        PairPlayerEntryDto entry2 = dto.getPlayers().get(1);

        if (entry1.getPlayerId().equals(entry2.getPlayerId())) {
            throw new BusinessException("Los dos jugadores de la pareja deben ser distintos");
        }

        Player player1 = getPlayerOrThrow(entry1.getPlayerId());
        Player player2 = getPlayerOrThrow(entry2.getPlayerId());

        // Validar que ninguno esté ya en una pareja de este torneo
        if (playerRepository.existsPlayerInTournament(entry1.getPlayerId(), tournamentId)) {
            throw new BusinessException(
                    player1.getFirstName() + " " + player1.getLastName()
                    + " ya pertenece a una pareja en este torneo");
        }
        if (playerRepository.existsPlayerInTournament(entry2.getPlayerId(), tournamentId)) {
            throw new BusinessException(
                    player2.getFirstName() + " " + player2.getLastName()
                    + " ya pertenece a una pareja en este torneo");
        }

        // Resolver categorías elegidas por cada jugador
        Category cat1 = categoryRepository.findById(entry1.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría", entry1.getCategoryId()));
        Category cat2 = categoryRepository.findById(entry2.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría", entry2.getCategoryId()));

        // Calcular puntos de cada jugador en su categoría elegida
        double points1 = getPlayerPoints(entry1.getPlayerId(), cat1.getId());
        double points2 = getPlayerPoints(entry2.getPlayerId(), cat2.getId());

        Pair pair = Pair.builder()
                .tournament(tournament)
                .totalPoints(points1 + points2)
                .build();
        pair = pairRepository.save(pair);

        pairPlayerRepository.save(PairPlayer.builder().pair(pair).player(player1).category(cat1).build());
        pairPlayerRepository.save(PairPlayer.builder().pair(pair).player(player2).category(cat2).build());

        return toDto(pair);
    }

    @Transactional
    public void delete(Long tournamentId, Long pairId) {
        Pair pair = getPairOrThrow(tournamentId, pairId);

        // No se puede borrar una pareja si el torneo ya tiene resultados registrados
        boolean anyPlayed = matchRepository.findByTournamentIdAndPhase(tournamentId, MatchPhase.ZONE)
                .stream().anyMatch(m -> m.getStatus() == MatchStatus.PLAYED);
        if (anyPlayed) {
            throw new BusinessException(
                    "No se puede borrar una pareja: el torneo ya tiene partidos con resultado registrado.");
        }

        // Borrar una pareja cambia la cantidad de inscriptos → las zonas y el fixture
        // quedan inválidos. Se borran ambos; el admin debe regenerar zonas y fixture.
        clearFixtureAndZones(tournamentId);

        pairRepository.delete(pair);
    }

    /**
     * Borra el fixture (partidos de zona + eliminación) y las zonas del torneo.
     * Se usa al eliminar una pareja: cambia N y todo debe regenerarse.
     * Sólo se invoca tras validar que no hay resultados registrados.
     */
    private void clearFixtureAndZones(Long tournamentId) {
        // 1. Partidos (referencian zonas y parejas) — primero por FK
        List<Match> matches = new ArrayList<>();
        matches.addAll(matchRepository.findByTournamentIdAndPhase(tournamentId, MatchPhase.ZONE));
        matches.addAll(matchRepository.findByTournamentIdAndPhase(tournamentId, MatchPhase.ELIMINATION));
        if (!matches.isEmpty()) {
            matchRepository.deleteAll(matches);
            matchRepository.flush();
        }
        // 2. Zonas (el cascade ALL de Zone.zonePairs borra los ZonePair)
        List<Zone> zones = zoneRepository.findByTournamentIdOrderByZoneOrder(tournamentId);
        if (!zones.isEmpty()) {
            zoneRepository.deleteAll(zones);
            zoneRepository.flush();
        }
    }

    // ── Preferencias y restricciones horarias ─────────────────────────────────

    @Transactional
    public PairScheduleConstraintResponseDto addConstraint(Long tournamentId, Long pairId,
                                                           PairScheduleConstraintRequestDto dto) {
        Pair pair = getPairOrThrow(tournamentId, pairId);

        if (matchRepository.existsPlayedMatchesByTournamentId(tournamentId)) {
            throw new BusinessException(
                    "No se pueden agregar restricciones: ya hay resultados cargados");
        }
        if (dto.getSlotEnd().isBefore(dto.getSlotStart()) || dto.getSlotEnd().equals(dto.getSlotStart())) {
            throw new BusinessException("La hora de fin debe ser posterior a la hora de inicio");
        }

        PairScheduleConstraint constraint = PairScheduleConstraint.builder()
                .pair(pair)
                .constraintType(dto.getConstraintType())
                .dayOfWeek(dto.getDayOfWeek())
                .slotStart(dto.getSlotStart())
                .slotEnd(dto.getSlotEnd())
                .build();

        return toConstraintDto(constraintRepository.save(constraint));
    }

    @Transactional
    public void removeConstraint(Long tournamentId, Long pairId, Long constraintId) {
        getPairOrThrow(tournamentId, pairId);
        if (matchRepository.existsPlayedMatchesByTournamentId(tournamentId)) {
            throw new BusinessException(
                    "No se pueden eliminar restricciones: ya hay resultados cargados");
        }
        PairScheduleConstraint constraint = constraintRepository.findById(constraintId)
                .orElseThrow(() -> new ResourceNotFoundException("Restricción", constraintId));
        if (!constraint.getPair().getId().equals(pairId)) {
            throw new BusinessException("La restricción no pertenece a esta pareja");
        }
        constraintRepository.delete(constraint);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private double getPlayerPoints(Long playerId, Long categoryId) {
        return pointsRepository.findByPlayerIdAndCategoryId(playerId, categoryId)
                .map(PlayerCategoryPoints::getPoints)
                .orElse(0.0);
    }

    private Tournament getTournamentOrThrow(Long id) {
        return tournamentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", id));
    }

    private Player getPlayerOrThrow(Long id) {
        return playerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador", id));
    }

    private Pair getPairOrThrow(Long tournamentId, Long pairId) {
        Pair pair = pairRepository.findById(pairId)
                .orElseThrow(() -> new ResourceNotFoundException("Pareja", pairId));
        if (!pair.getTournament().getId().equals(tournamentId)) {
            throw new BusinessException("La pareja no pertenece al torneo indicado");
        }
        return pair;
    }

    private PairResponseDto toDto(Pair pair) {
        Long defaultCategoryId = pair.getTournament().getCategory().getId();

        List<PairPlayerResponseDto> players = pairPlayerRepository.findByPairId(pair.getId()).stream()
                .map(pp -> {
                    Player p = pp.getPlayer();
                    // Usa la categoría elegida para el jugador, o la del torneo como fallback
                    Category effectiveCat = pp.getCategory() != null
                            ? pp.getCategory()
                            : pair.getTournament().getCategory();
                    double pts = getPlayerPoints(p.getId(), effectiveCat.getId());
                    return PairPlayerResponseDto.builder()
                            .id(p.getId())
                            .firstName(p.getFirstName())
                            .lastName(p.getLastName())
                            .phone(p.getPhone())
                            .categoryId(effectiveCat.getId())
                            .categoryName(effectiveCat.getName())
                            .points(pts)
                            .build();
                })
                .toList();

        List<PairScheduleConstraintResponseDto> constraints =
                constraintRepository.findByPairId(pair.getId()).stream()
                        .map(this::toConstraintDto)
                        .toList();

        return PairResponseDto.builder()
                .id(pair.getId())
                .tournamentId(pair.getTournament().getId())
                .totalPoints(pair.getTotalPoints())
                .players(players)
                .constraints(constraints)
                .build();
    }

    private PairScheduleConstraintResponseDto toConstraintDto(PairScheduleConstraint c) {
        return PairScheduleConstraintResponseDto.builder()
                .id(c.getId())
                .constraintType(c.getConstraintType())
                .dayOfWeek(c.getDayOfWeek())
                .dayName(DAY_NAMES[c.getDayOfWeek() - 1])
                .slotStart(c.getSlotStart())
                .slotEnd(c.getSlotEnd())
                .build();
    }
}
