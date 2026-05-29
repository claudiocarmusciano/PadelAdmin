package com.padeladmin.padeladmin.service;

import com.padeladmin.padeladmin.entity.*;
import com.padeladmin.padeladmin.enums.MatchPhase;
import com.padeladmin.padeladmin.enums.MatchStatus;
import com.padeladmin.padeladmin.enums.TournamentStage;
import com.padeladmin.padeladmin.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Otorga los puntos de ranking (configurados en Settings/PointConfig) a los jugadores
 * según la MEJOR instancia alcanzada por su pareja en un torneo.
 *
 * Política (definida con el usuario):
 *  - Se otorgan al pasar el torneo a COMPLETED.
 *  - Sólo la mejor instancia (no acumulativo por etapa).
 *  - Acumulativos sobre el puntaje vigente del jugador en esa categoría.
 *  - Idempotente: si el torneo ya tiene puntos otorgados, no se duplica.
 *  - El historial (TournamentPointAward) sobrevive al borrado del torneo y a la limpieza de puntos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TournamentPointsService {

    private final PairRepository pairRepository;
    private final PairPlayerRepository pairPlayerRepository;
    private final MatchRepository matchRepository;
    private final MatchResultRepository matchResultRepository;
    private final PointConfigRepository pointConfigRepository;
    private final PlayerCategoryPointsRepository playerCategoryPointsRepository;
    private final TournamentPointAwardRepository awardRepository;

    /** Ranking de stages: menor número = más avance. */
    private static final Map<String, Integer> STAGE_RANK = Map.ofEntries(
            Map.entry("CHAMPION", 0),
            Map.entry("FINALIST", 1),
            Map.entry("SEMIFINAL", 2),
            Map.entry("QUARTERFINAL", 3),
            Map.entry("ROUND_8", 4),
            Map.entry("ROUND_16", 5),
            Map.entry("ROUND_32", 6),
            Map.entry("ZONE_PASS", 7),
            Map.entry("PARTICIPANT", 8)
    );

    /**
     * Otorga los puntos para un torneo. Idempotente.
     * @return cantidad de jugadores que recibieron puntos (> 0)
     */
    @Transactional
    public int awardPointsForTournament(Tournament tournament) {
        Long tId = tournament.getId();

        if (awardRepository.existsByTournamentId(tId)) {
            log.info("Torneo {} ya tiene puntos otorgados — se omite (idempotente)", tId);
            return 0;
        }

        Category category = tournament.getCategory();
        if (category == null) {
            log.warn("Torneo {} sin categoría — no se otorgan puntos", tId);
            return 0;
        }

        // Mapa stage → puntos configurados
        Map<TournamentStage, BigDecimal> pointsByStage = new EnumMap<>(TournamentStage.class);
        for (PointConfig pc : pointConfigRepository.findAll()) {
            pointsByStage.put(pc.getStage(), pc.getPoints());
        }

        List<Pair> pairs = pairRepository.findByTournamentIdOrderByTotalPointsDesc(tId);
        int awardedPlayers = 0;

        List<PlayerCategoryPoints> toSavePoints = new ArrayList<>();
        List<TournamentPointAward> toSaveAwards = new ArrayList<>();

        for (Pair pair : pairs) {
            TournamentStage stage = bestStageForPair(pair.getId(), tId);
            if (stage == null) continue; // pareja sin partidos

            BigDecimal pts = pointsByStage.getOrDefault(stage, BigDecimal.ZERO);
            if (pts.compareTo(BigDecimal.ZERO) <= 0) continue; // stage no configurado o en 0

            List<PairPlayer> players = pairPlayerRepository.findByPairId(pair.getId());
            for (PairPlayer pp : players) {
                Player player = pp.getPlayer();

                // Acumular sobre el puntaje vigente del jugador en esta categoría
                PlayerCategoryPoints entry = playerCategoryPointsRepository
                        .findByPlayerIdAndCategoryId(player.getId(), category.getId())
                        .orElseGet(() -> PlayerCategoryPoints.builder()
                                .player(player)
                                .category(category)
                                .points(0.0)
                                .build());
                entry.setPoints((entry.getPoints() == null ? 0.0 : entry.getPoints()) + pts.doubleValue());
                toSavePoints.add(entry);

                // Registro histórico (snapshot — sobrevive borrados/limpiezas)
                toSaveAwards.add(TournamentPointAward.builder()
                        .tournamentId(tId)
                        .tournamentName(tournament.getName())
                        .categoryId(category.getId())
                        .categoryName(category.getName())
                        .playerId(player.getId())
                        .playerName(player.getLastName() + ", " + player.getFirstName())
                        .pairId(pair.getId())
                        .stage(stage)
                        .points(pts)
                        .build());

                awardedPlayers++;
            }
        }

        playerCategoryPointsRepository.saveAll(toSavePoints);
        awardRepository.saveAll(toSaveAwards);

        log.info("Torneo {} finalizado: puntos otorgados a {} jugadores ({} registros de historial)",
                tId, awardedPlayers, toSaveAwards.size());
        return awardedPlayers;
    }

    /**
     * Limpieza de puntos para arrancar una nueva temporada/semestre.
     * Pone en 0 todos los puntos vigentes de los jugadores. El historial NO se toca.
     * @return cantidad de registros de puntos reseteados
     */
    @Transactional
    public int resetAllPoints() {
        List<PlayerCategoryPoints> all = playerCategoryPointsRepository.findAll();
        for (PlayerCategoryPoints p : all) {
            p.setPoints(0.0);
        }
        playerCategoryPointsRepository.saveAll(all);
        log.info("Limpieza de puntos: {} registros reseteados a 0 (historial conservado)", all.size());
        return all.size();
    }

    // ── Determinación de la mejor instancia alcanzada por una pareja ────────────

    private TournamentStage bestStageForPair(Long pairId, Long tournamentId) {
        List<Match> matches = matchRepository.findByPairAndTournament(pairId, tournamentId);
        if (matches.isEmpty()) return null;

        boolean hasZone = false;
        boolean hasElimInBracket = false;
        String best = null;

        for (Match m : matches) {
            if (m.getPhase() == MatchPhase.ZONE) {
                hasZone = true;
                continue;
            }
            // ELIMINATION
            hasElimInBracket = true;

            if (m.getStatus() != MatchStatus.PLAYED) continue;
            Optional<MatchResult> resOpt = matchResultRepository.findByMatchId(m.getId());
            if (resOpt.isEmpty()) continue;
            MatchResult res = resOpt.get();

            boolean won = res.getWinnerPair() != null && res.getWinnerPair().getId().equals(pairId);
            String candidate = stageFromRound(m.getEliminationRound(), won);
            best = lowerRank(best, candidate);
        }

        String finalStage;
        if (best != null) {
            finalStage = best;
        } else if (hasElimInBracket) {
            finalStage = "ZONE_PASS";   // clasificó pero su partido eliminatorio no quedó resuelto
        } else if (hasZone) {
            finalStage = "PARTICIPANT"; // jugó zona pero no clasificó
        } else {
            return null;
        }
        return TournamentStage.valueOf(finalStage);
    }

    /** Deriva la etapa según la ronda eliminatoria y si ganó (incluye reconocer el avance). */
    private String stageFromRound(Integer round, boolean won) {
        if (round == null) return "PARTICIPANT";

        String candidate;
        if (round == 1) candidate = won ? "CHAMPION" : "FINALIST";
        else if (round == 2) candidate = "SEMIFINAL";
        else if (round == 4) candidate = "QUARTERFINAL";
        else if (round == 8) candidate = "ROUND_8";
        else if (round == 16) candidate = "ROUND_16";
        else if (round == 32) candidate = "ROUND_32";
        else candidate = "PARTICIPANT";

        // Si ganó una ronda > final, reconocer que llegó al menos a la siguiente
        if (won && round > 1) {
            int next = round / 2;
            String advanced;
            if (next == 1) advanced = "FINALIST";
            else if (next == 2) advanced = "SEMIFINAL";
            else if (next == 4) advanced = "QUARTERFINAL";
            else if (next == 8) advanced = "ROUND_8";
            else if (next == 16) advanced = "ROUND_16";
            else advanced = candidate;
            if (STAGE_RANK.get(advanced) < STAGE_RANK.get(candidate)) {
                candidate = advanced;
            }
        }
        return candidate;
    }

    private String lowerRank(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return STAGE_RANK.get(a) <= STAGE_RANK.get(b) ? a : b;
    }
}
