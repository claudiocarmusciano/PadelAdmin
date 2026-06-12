package com.padeladmin.padeladmin.service;

import com.padeladmin.padeladmin.dto.fixture.MatchPairDto;
import com.padeladmin.padeladmin.dto.match.*;
import com.padeladmin.padeladmin.entity.*;
import com.padeladmin.padeladmin.enums.MatchPhase;
import com.padeladmin.padeladmin.enums.MatchStatus;
import com.padeladmin.padeladmin.enums.ZoneRound2Type;
import com.padeladmin.padeladmin.entity.MatchSet;
import com.padeladmin.padeladmin.exception.BusinessException;
import com.padeladmin.padeladmin.exception.ResourceNotFoundException;
import com.padeladmin.padeladmin.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchResultService {

    private final MatchRepository matchRepository;
    private final MatchResultRepository matchResultRepository;
    private final ZoneRepository zoneRepository;
    private final ZonePairRepository zonePairRepository;
    private final PairPlayerRepository pairPlayerRepository;
    private final EliminationService eliminationService;

    // ── Registrar resultado ────────────────────────────────────────────────────

    @Transactional
    public MatchResultResponseDto recordResult(Long matchId, MatchResultRequestDto dto) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido", matchId));

        if (match.getStatus() == MatchStatus.PLAYED) {
            throw new BusinessException("El partido ya tiene resultado cargado");
        }
        if (match.getStatus() == MatchStatus.CANCELLED) {
            throw new BusinessException("No se puede cargar resultado de un partido cancelado");
        }
        if (match.getPair1() == null) {
            throw new BusinessException(
                    "El partido aún no tiene parejas definidas: faltan los resultados de la Ronda 1 de la zona");
        }
        if (match.getPair2() == null || match.isBye()) {
            throw new BusinessException("No se puede cargar resultado de un partido con BYE");
        }

        // Recuperación: si quedó un resultado huérfano (ej: se reprogramó un partido jugado y su
        // estado se había degradado), lo borramos antes de crear el nuevo para no chocar con la FK.
        matchResultRepository.findByMatchId(matchId).ifPresent(existing -> {
            matchResultRepository.delete(existing);
            matchResultRepository.flush();
        });

        int pair1Sets, pair2Sets;
        Pair winner;
        List<SetScoreDto> setScores;

        if (dto.isWalkover()) {
            // ── Modo W.O. ─────────────────────────────────────────────────────
            Long walkoverId = dto.getWalkoverId();
            if (walkoverId == null) {
                throw new BusinessException("Debe indicar qué pareja dio W.O.");
            }
            boolean walkoverId1 = walkoverId.equals(match.getPair1().getId());
            boolean walkoverId2 = walkoverId.equals(match.getPair2().getId());
            if (!walkoverId1 && !walkoverId2) {
                throw new BusinessException("La pareja que dio W.O. no pertenece a este partido");
            }
            // Ganador = la pareja que SÍ se presentó
            winner = walkoverId1 ? match.getPair2() : match.getPair1();
            // Sets automáticos: ganador 6-0, 6-0
            pair1Sets = walkoverId1 ? 0 : 2;
            pair2Sets = walkoverId1 ? 2 : 0;
            setScores = List.of(
                    buildSet(walkoverId1 ? 0 : 6, walkoverId1 ? 6 : 0),
                    buildSet(walkoverId1 ? 0 : 6, walkoverId1 ? 6 : 0)
            );

            MatchResult result = MatchResult.builder()
                    .match(match)
                    .pair1Score(pair1Sets)
                    .pair2Score(pair2Sets)
                    .winnerPair(winner)
                    .walkover(true)
                    .walkoverId(walkoverId)
                    .build();
            matchResultRepository.save(result);

            for (int i = 0; i < setScores.size(); i++) {
                SetScoreDto s = setScores.get(i);
                result.getSets().add(MatchSet.builder()
                        .matchResult(result)
                        .setNumber(i + 1)
                        .pair1Games(s.getPair1Games())
                        .pair2Games(s.getPair2Games())
                        .build());
            }
            matchResultRepository.save(result);

            match.setStatus(MatchStatus.PLAYED);
            matchRepository.save(match);

            boolean round2Created = false;
            if (match.getPhase() == MatchPhase.ZONE
                    && match.getZone() != null
                    && match.getZone().getZoneSize() == 4
                    && Integer.valueOf(1).equals(match.getZoneRound())) {
                round2Created = tryCreateRound2(match.getZone(), match.getTournament());
            }
            if (match.getPhase() == MatchPhase.ELIMINATION) {
                eliminationService.advanceWinner(match, winner);
            }

            log.info("W.O. registrado: Match {} → Ganador Pareja {} (W.O. Pareja {})", matchId, winner.getId(), walkoverId);
            return toDto(result, match, round2Created);
        }

        // ── Resultado normal ───────────────────────────────────────────────────
        setScores = dto.getSets();
        validateSets(setScores);

        pair1Sets = 0; pair2Sets = 0;
        for (SetScoreDto s : setScores) {
            if (s.getPair1Games() > s.getPair2Games()) pair1Sets++;
            else pair2Sets++;
        }

        if (pair1Sets == pair2Sets) {
            throw new BusinessException(
                    "Los sets ingresados no determinan un ganador claro. " +
                    "Verificá los resultados de cada set.");
        }

        winner = pair1Sets > pair2Sets ? match.getPair1() : match.getPair2();

        // Guardar resultado principal
        MatchResult result = MatchResult.builder()
                .match(match)
                .pair1Score(pair1Sets)
                .pair2Score(pair2Sets)
                .winnerPair(winner)
                .build();
        matchResultRepository.save(result);

        // Guardar detalle de cada set
        for (int i = 0; i < setScores.size(); i++) {
            SetScoreDto s = setScores.get(i);
            MatchSet matchSet = MatchSet.builder()
                    .matchResult(result)
                    .setNumber(i + 1)
                    .pair1Games(s.getPair1Games())
                    .pair2Games(s.getPair2Games())
                    .build();
            result.getSets().add(matchSet);
        }
        matchResultRepository.save(result); // guarda los sets por cascada

        // Actualizar estado del partido
        match.setStatus(MatchStatus.PLAYED);
        matchRepository.save(match);

        // Zona de 4: verificar si hay que crear ronda 2
        boolean round2Created = false;
        if (match.getPhase() == MatchPhase.ZONE
                && match.getZone() != null
                && match.getZone().getZoneSize() == 4
                && Integer.valueOf(1).equals(match.getZoneRound())) {
            round2Created = tryCreateRound2(match.getZone(), match.getTournament());
        }

        // Si se cargó/editó un resultado de ZONA y existe un bracket que todavía no empezó,
        // lo invalidamos para que se regenere con la clasificación corregida.
        if (match.getPhase() == MatchPhase.ZONE) {
            eliminationService.invalidateBracketIfNotStarted(match.getTournament().getId());
        }

        // Fase eliminatoria: avanzar el ganador al siguiente partido del bracket
        if (match.getPhase() == MatchPhase.ELIMINATION) {
            eliminationService.advanceWinner(match, winner);
        }

        log.info("Resultado registrado: Match {} → {}sets a {}sets (Ganador: Pareja {})",
                matchId, pair1Sets, pair2Sets, winner.getId());

        return toDto(result, match, round2Created);
    }

    // ── Editar resultado ───────────────────────────────────────────────────────

    @Transactional
    public MatchResultResponseDto updateResult(Long matchId, MatchResultRequestDto dto) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido", matchId));

        if (match.getStatus() != MatchStatus.PLAYED) {
            throw new BusinessException("El partido no tiene resultado registrado");
        }

        MatchResult existingResult = matchResultRepository.findByMatchId(matchId)
                .orElseThrow(() -> new BusinessException("No se encontró el resultado del partido"));

        // Para fase eliminatoria: bloquear edición si el siguiente partido ya fue jugado
        if (match.getPhase() == MatchPhase.ELIMINATION
                && match.getEliminationRound() != null
                && match.getEliminationRound() > 1) {
            int nextRound = match.getEliminationRound() / 2;
            int nextSlot  = (match.getBracketSlot() + 1) / 2;
            matchRepository.findByTournamentIdAndPhaseAndEliminationRoundAndBracketSlot(
                            match.getTournament().getId(), MatchPhase.ELIMINATION, nextRound, nextSlot)
                    .ifPresent(nextMatch -> {
                        if (nextMatch.getStatus() == MatchStatus.PLAYED) {
                            throw new BusinessException(
                                    "No se puede editar: el ganador ya jugó el siguiente partido del bracket");
                        }
                    });
        }

        // Deshacer el avance del ganador en bracket eliminatorio
        if (match.getPhase() == MatchPhase.ELIMINATION) {
            reverseWinnerAdvancement(match, existingResult.getWinnerPair());
        }

        // Eliminar resultado viejo (los sets se eliminan por cascade)
        matchResultRepository.delete(existingResult);
        matchResultRepository.flush();

        // Revertir estado del partido a pendiente
        match.setStatus(MatchStatus.PENDING);
        matchRepository.save(match);

        log.info("Resultado editado: Match {} — resultado anterior eliminado, registrando nuevo", matchId);

        // Registrar el nuevo resultado usando la lógica existente
        return recordResult(matchId, dto);
    }

    // Revierte el avance del ganador al siguiente partido del bracket
    private void reverseWinnerAdvancement(Match match, Pair oldWinner) {
        if (match.getEliminationRound() == null || match.getEliminationRound() <= 1) return;

        int nextRound = match.getEliminationRound() / 2;
        int nextSlot  = (match.getBracketSlot() + 1) / 2;
        boolean isPair1 = match.getBracketSlot() % 2 == 1;

        matchRepository.findByTournamentIdAndPhaseAndEliminationRoundAndBracketSlot(
                        match.getTournament().getId(), MatchPhase.ELIMINATION, nextRound, nextSlot)
                .ifPresent(nextMatch -> {
                    if (nextMatch.getStatus() != MatchStatus.PLAYED) {
                        if (isPair1) nextMatch.setPair1(null);
                        else         nextMatch.setPair2(null);
                        matchRepository.save(nextMatch);
                        log.info("Revertido avance: viejo ganador Pareja {} removido de ronda {} slot {}",
                                oldWinner.getId(), nextRound, nextSlot);
                    }
                });
    }

    // ── Obtener resultado ──────────────────────────────────────────────────────

    public MatchResultResponseDto getResult(Long matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido", matchId));
        MatchResult result = matchResultRepository.findByMatchId(matchId)
                .orElseThrow(() -> new BusinessException("El partido no tiene resultado registrado aún"));
        return toDto(result, match, false);
    }

    // ── Tabla de posiciones de una zona ───────────────────────────────────────

    public List<ZoneStandingDto> getZoneStandings(Long zoneId) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zona", zoneId));

        List<ZonePair> zonePairs = zonePairRepository.findByZoneIdOrderByPosition(zoneId);
        List<Match> allMatches = matchRepository.findByZoneId(zoneId);

        Map<Long, MatchResult> resultByMatchId = allMatches.stream()
                .filter(m -> m.getStatus() == MatchStatus.PLAYED)
                .map(m -> matchResultRepository.findByMatchId(m.getId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(r -> r.getMatch().getId(), r -> r));

        // Acumular estadísticas por pareja
        Map<Long, StandingAccumulator> accMap = new LinkedHashMap<>();
        for (ZonePair zp : zonePairs) {
            accMap.put(zp.getPair().getId(), new StandingAccumulator());
        }

        // Mapa cabeza a cabeza: key = "min-max pairId", value = pairId ganador
        Map<String, Long> h2hMap = new HashMap<>();

        for (Match match : allMatches) {
            if (match.getStatus() != MatchStatus.PLAYED) continue;
            MatchResult result = resultByMatchId.get(match.getId());
            if (result == null) continue;

            Long pair1Id = match.getPair1().getId();
            Long pair2Id = match.getPair2().getId();
            Long winnerId = result.getWinnerPair().getId();

            // Cabeza a cabeza
            String h2hKey = Math.min(pair1Id, pair2Id) + "-" + Math.max(pair1Id, pair2Id);
            h2hMap.put(h2hKey, winnerId);

            StandingAccumulator acc1 = accMap.get(pair1Id);
            StandingAccumulator acc2 = accMap.get(pair2Id);
            if (acc1 == null || acc2 == null) continue;

            acc1.played++;
            acc2.played++;
            acc1.setsFor += result.getPair1Score();
            acc1.setsAgainst += result.getPair2Score();
            acc2.setsFor += result.getPair2Score();
            acc2.setsAgainst += result.getPair1Score();

            // Games acumulados de todos los sets
            for (MatchSet s : result.getSets()) {
                acc1.gamesFor += s.getPair1Games();
                acc1.gamesAgainst += s.getPair2Games();
                acc2.gamesFor += s.getPair2Games();
                acc2.gamesAgainst += s.getPair1Games();
            }

            if (winnerId.equals(pair1Id)) {
                acc1.wins++;
                acc2.losses++;
                if (result.isWalkover() && pair2Id.equals(result.getWalkoverId())) {
                    acc2.walkovers++;
                }
            } else {
                acc2.wins++;
                acc1.losses++;
                if (result.isWalkover() && pair1Id.equals(result.getWalkoverId())) {
                    acc1.walkovers++;
                }
            }
        }

        // ── Ordenar según reglas oficiales ────────────────────────────────────
        // Zona de 3: victorias → dif.sets → dif.games → games+ → games- → H2H → sorteo
        // Zona de 4: victorias → H2H → dif.sets → dif.games → games+ → games- → sorteo
        int classifyCount = zone.getZoneSize() == 3 ? 2 : 3;
        boolean isZone4 = zone.getZoneSize() == 4;
        List<ZonePair> sorted = new ArrayList<>(zonePairs);
        sorted.sort((a, b) -> {
            StandingAccumulator accA = accMap.get(a.getPair().getId());
            StandingAccumulator accB = accMap.get(b.getPair().getId());

            // 1. Victorias
            if (!accA.wins.equals(accB.wins)) return accB.wins - accA.wins;

            // Zona 4: resultado en cancha primero
            if (isZone4) {
                int h2h = h2hCompare(a.getPair().getId(), b.getPair().getId(), h2hMap);
                if (h2h != 0) return h2h;
            }

            // 2. Diferencia de sets
            int setDiffA = accA.setsFor - accA.setsAgainst;
            int setDiffB = accB.setsFor - accB.setsAgainst;
            if (setDiffA != setDiffB) return setDiffB - setDiffA;

            // 3. Diferencia de games
            int gameDiffA = accA.gamesFor - accA.gamesAgainst;
            int gameDiffB = accB.gamesFor - accB.gamesAgainst;
            if (gameDiffA != gameDiffB) return gameDiffB - gameDiffA;

            // 4. Games a favor
            if (!accA.gamesFor.equals(accB.gamesFor)) return accB.gamesFor - accA.gamesFor;

            // 5. Games en contra (menos es mejor)
            if (!accA.gamesAgainst.equals(accB.gamesAgainst)) return accA.gamesAgainst - accB.gamesAgainst;

            // Zona 3: resultado en cancha al final (antes del sorteo)
            if (!isZone4) {
                return h2hCompare(a.getPair().getId(), b.getPair().getId(), h2hMap);
            }

            return 0; // sorteo (no automatizable)
        });

        List<ZoneStandingDto> standings = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            ZonePair zp = sorted.get(i);
            StandingAccumulator acc = accMap.get(zp.getPair().getId());
            List<PairPlayer> players = pairPlayerRepository.findByPairId(zp.getPair().getId());

            // Pts. torneo: 2 por victoria, 1 por derrota presente, 0 por W.O.
            int presentLosses = acc.losses - acc.walkovers;
            int tournamentPoints = acc.wins * 2 + presentLosses;

            standings.add(ZoneStandingDto.builder()
                    .position(i + 1)
                    .pairId(zp.getPair().getId())
                    .player1(players.size() > 0
                            ? players.get(0).getPlayer().getLastName() + " " + players.get(0).getPlayer().getFirstName()
                            : "-")
                    .player2(players.size() > 1
                            ? players.get(1).getPlayer().getLastName() + " " + players.get(1).getPlayer().getFirstName()
                            : "-")
                    .totalPoints(zp.getPair().getTotalPoints())
                    .tournamentPoints(tournamentPoints)
                    .played(acc.played)
                    .wins(acc.wins)
                    .losses(acc.losses)
                    .walkovers(acc.walkovers)
                    .setsFor(acc.setsFor)
                    .setsAgainst(acc.setsAgainst)
                    .setsDiff(acc.setsFor - acc.setsAgainst)
                    .classified(i < classifyCount)
                    .build());
        }

        return standings;
    }

    // ── Helper sets ──────────────────────────────────────────────────────────

    private SetScoreDto buildSet(int pair1Games, int pair2Games) {
        SetScoreDto s = new SetScoreDto();
        s.setPair1Games(pair1Games);
        s.setPair2Games(pair2Games);
        return s;
    }

    // ── Validaciones de sets ──────────────────────────────────────────────────

    private void validateSets(List<SetScoreDto> sets) {
        for (int i = 0; i < sets.size(); i++) {
            SetScoreDto s = sets.get(i);
            if (s.getPair1Games().equals(s.getPair2Games())) {
                throw new BusinessException(
                        "El set " + (i + 1) + " no puede terminar en empate (" +
                        s.getPair1Games() + "-" + s.getPair2Games() + ")");
            }
        }

        // Validar que el número de sets sea consistente con el ganador del partido
        // (no se puede tener 3 sets si alguno ganó 2-0)
        int pair1SetsWon = 0, pair2SetsWon = 0;
        for (SetScoreDto s : sets) {
            if (s.getPair1Games() > s.getPair2Games()) pair1SetsWon++;
            else pair2SetsWon++;
        }
        int totalSets = sets.size();
        if (totalSets == 3 && (pair1SetsWon == 3 || pair2SetsWon == 3)) {
            throw new BusinessException(
                    "Si una pareja gana los 3 sets, el partido debería terminar en 2 sets. " +
                    "Revisá si el tercer set fue necesario.");
        }
    }

    // ── Ronda 2 zona de 4 ─────────────────────────────────────────────────────

    private boolean tryCreateRound2(Zone zone, Tournament tournament) {
        List<Match> round1Matches = matchRepository.findByZoneIdAndZoneRound(zone.getId(), 1);

        boolean allPlayed = round1Matches.size() == 2
                && round1Matches.stream().allMatch(m -> m.getStatus() == MatchStatus.PLAYED);
        if (!allPlayed) return false;

        Match m1 = round1Matches.get(0);
        Match m2 = round1Matches.get(1);

        MatchResult r1 = matchResultRepository.findByMatchId(m1.getId()).orElseThrow();
        MatchResult r2 = matchResultRepository.findByMatchId(m2.getId()).orElseThrow();

        Pair winner1 = r1.getWinnerPair();
        Pair loser1 = winner1.getId().equals(m1.getPair1().getId()) ? m1.getPair2() : m1.getPair1();
        Pair winner2 = r2.getWinnerPair();
        Pair loser2 = winner2.getId().equals(m2.getPair1().getId()) ? m2.getPair2() : m2.getPair1();

        List<Match> round2 = matchRepository.findByZoneIdAndZoneRound(zone.getId(), 2);

        if (!round2.isEmpty()) {
            // Placeholders creados al generar el fixture (ya tienen cancha y horario, ambos en
            // el mismo horario): rellenar las parejas. Si se corrige un resultado de R1, se
            // re-rellenan — salvo que la R2 ya se haya jugado.
            boolean changed = false;
            for (Match m : round2) {
                if (m.getStatus() == MatchStatus.PLAYED) continue;
                boolean losersMatch = m.getZoneRound2Type() == ZoneRound2Type.LOSERS;
                Pair a = losersMatch ? loser1 : winner1;
                Pair b = losersMatch ? loser2 : winner2;
                Long curA = m.getPair1() != null ? m.getPair1().getId() : null;
                Long curB = m.getPair2() != null ? m.getPair2().getId() : null;
                if (!a.getId().equals(curA) || !b.getId().equals(curB)) {
                    m.setPair1(a);
                    m.setPair2(b);
                    changed = true;
                }
            }
            if (changed) {
                matchRepository.saveAll(round2);
                log.info("Ronda 2 completada — Zona {}: Ganadores {} vs {} | Perdedores {} vs {}",
                        zone.getName(), winner1.getId(), winner2.getId(), loser1.getId(), loser2.getId());
            }
            return changed;
        }

        // Fallback (torneos con fixture generado antes de los placeholders): crear los partidos
        Match winnersMatch = Match.builder()
                .tournament(tournament).phase(MatchPhase.ZONE).zone(zone)
                .pair1(winner1).pair2(winner2).zoneRound(2)
                .zoneRound2Type(ZoneRound2Type.WINNERS).status(MatchStatus.PENDING).build();

        Match losersMatch = Match.builder()
                .tournament(tournament).phase(MatchPhase.ZONE).zone(zone)
                .pair1(loser1).pair2(loser2).zoneRound(2)
                .zoneRound2Type(ZoneRound2Type.LOSERS).status(MatchStatus.PENDING).build();

        matchRepository.saveAll(List.of(winnersMatch, losersMatch));

        log.info("Ronda 2 creada — Zona {}: Ganadores {} vs {} | Perdedores {} vs {}",
                zone.getName(), winner1.getId(), winner2.getId(), loser1.getId(), loser2.getId());

        return true;
    }

    // ── Mapeo a DTO ───────────────────────────────────────────────────────────

    private MatchResultResponseDto toDto(MatchResult result, Match match, boolean round2Created) {
        List<SetScoreResponseDto> setDtos = result.getSets().stream()
                .map(s -> {
                    Long setWinner = s.getPair1Games() > s.getPair2Games()
                            ? match.getPair1().getId()
                            : match.getPair2().getId();
                    return SetScoreResponseDto.builder()
                            .setNumber(s.getSetNumber())
                            .pair1Games(s.getPair1Games())
                            .pair2Games(s.getPair2Games())
                            .winnerPairId(setWinner)
                            .build();
                })
                .toList();

        return MatchResultResponseDto.builder()
                .matchId(match.getId())
                .zoneName(match.getZone() != null ? "Zona " + match.getZone().getName() : null)
                .zoneRound(match.getZoneRound())
                .pair1(toPairDto(match.getPair1()))
                .pair2(match.getPair2() != null ? toPairDto(match.getPair2()) : null)
                .pair1Sets(result.getPair1Score())
                .pair2Sets(result.getPair2Score())
                .sets(setDtos)
                .winnerPairId(result.getWinnerPair().getId())
                .recordedAt(result.getRecordedAt())
                .round2Created(round2Created)
                .build();
    }

    private MatchPairDto toPairDto(Pair pair) {
        List<PairPlayer> players = pairPlayerRepository.findByPairId(pair.getId());
        String p1 = players.size() > 0
                ? players.get(0).getPlayer().getLastName() + " " + players.get(0).getPlayer().getFirstName()
                : "-";
        String p2 = players.size() > 1
                ? players.get(1).getPlayer().getLastName() + " " + players.get(1).getPlayer().getFirstName()
                : "-";
        return MatchPairDto.builder()
                .id(pair.getId())
                .player1(p1)
                .player2(p2)
                .totalPoints(pair.getTotalPoints())
                .build();
    }

    // ── Cabeza a cabeza ───────────────────────────────────────────────────────

    /**
     * Compara head-to-head entre dos parejas.
     * Retorna negativo si pairAId ganó (va antes), positivo si pairBId ganó, 0 si sin resultado.
     */
    private int h2hCompare(Long pairAId, Long pairBId, Map<String, Long> h2hMap) {
        String key = Math.min(pairAId, pairBId) + "-" + Math.max(pairAId, pairBId);
        Long winner = h2hMap.get(key);
        if (winner == null) return 0;
        return winner.equals(pairAId) ? -1 : 1;
    }

    // ── Acumulador interno ────────────────────────────────────────────────────

    private static class StandingAccumulator {
        Integer played = 0, wins = 0, losses = 0, walkovers = 0;
        Integer setsFor = 0, setsAgainst = 0;
        Integer gamesFor = 0, gamesAgainst = 0;
    }
}
