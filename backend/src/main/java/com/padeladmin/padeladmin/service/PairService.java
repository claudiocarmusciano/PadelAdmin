package com.padeladmin.padeladmin.service;

import com.padeladmin.padeladmin.dto.pair.*;
import com.padeladmin.padeladmin.entity.*;
import com.padeladmin.padeladmin.exception.BusinessException;
import com.padeladmin.padeladmin.exception.ResourceNotFoundException;
import com.padeladmin.padeladmin.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PlayerCategoryPointsRepository pointsRepository;

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

        Long playerId1 = dto.getPlayerIds().get(0);
        Long playerId2 = dto.getPlayerIds().get(1);

        if (playerId1.equals(playerId2)) {
            throw new BusinessException("Los dos jugadores de la pareja deben ser distintos");
        }

        Player player1 = getPlayerOrThrow(playerId1);
        Player player2 = getPlayerOrThrow(playerId2);

        // Validar que ninguno esté ya en una pareja de este torneo
        if (playerRepository.existsPlayerInTournament(playerId1, tournamentId)) {
            throw new BusinessException(
                    player1.getFirstName() + " " + player1.getLastName()
                    + " ya pertenece a una pareja en este torneo");
        }
        if (playerRepository.existsPlayerInTournament(playerId2, tournamentId)) {
            throw new BusinessException(
                    player2.getFirstName() + " " + player2.getLastName()
                    + " ya pertenece a una pareja en este torneo");
        }

        // Calcular puntos de cada jugador en la categoría del torneo
        int points1 = getPlayerPoints(playerId1, tournament.getCategory().getId());
        int points2 = getPlayerPoints(playerId2, tournament.getCategory().getId());

        Pair pair = Pair.builder()
                .tournament(tournament)
                .totalPoints(points1 + points2)
                .build();
        pair = pairRepository.save(pair);

        pairPlayerRepository.save(PairPlayer.builder().pair(pair).player(player1).build());
        pairPlayerRepository.save(PairPlayer.builder().pair(pair).player(player2).build());

        return toDto(pair);
    }

    @Transactional
    public void delete(Long tournamentId, Long pairId) {
        Pair pair = getPairOrThrow(tournamentId, pairId);
        pairRepository.delete(pair);
    }

    // ── Preferencias y restricciones horarias ─────────────────────────────────

    @Transactional
    public PairScheduleConstraintResponseDto addConstraint(Long tournamentId, Long pairId,
                                                           PairScheduleConstraintRequestDto dto) {
        Pair pair = getPairOrThrow(tournamentId, pairId);

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
        PairScheduleConstraint constraint = constraintRepository.findById(constraintId)
                .orElseThrow(() -> new ResourceNotFoundException("Restricción", constraintId));
        if (!constraint.getPair().getId().equals(pairId)) {
            throw new BusinessException("La restricción no pertenece a esta pareja");
        }
        constraintRepository.delete(constraint);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int getPlayerPoints(Long playerId, Long categoryId) {
        return pointsRepository.findByPlayerIdAndCategoryId(playerId, categoryId)
                .map(PlayerCategoryPoints::getPoints)
                .orElse(0);
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
        Long categoryId = pair.getTournament().getCategory().getId();

        List<PairPlayerResponseDto> players = pairPlayerRepository.findByPairId(pair.getId()).stream()
                .map(pp -> {
                    Player p = pp.getPlayer();
                    int pts = getPlayerPoints(p.getId(), categoryId);
                    return PairPlayerResponseDto.builder()
                            .id(p.getId())
                            .firstName(p.getFirstName())
                            .lastName(p.getLastName())
                            .phone(p.getPhone())
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
                .dayName(DAY_NAMES[c.getDayOfWeek()])
                .slotStart(c.getSlotStart())
                .slotEnd(c.getSlotEnd())
                .build();
    }
}
