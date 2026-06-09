package com.padeladmin.padeladmin.service;

import com.padeladmin.padeladmin.dto.player.PartnerStatDto;
import com.padeladmin.padeladmin.dto.player.PlayerStatsDto;
import com.padeladmin.padeladmin.dto.player.TournamentParticipationDto;
import com.padeladmin.padeladmin.entity.*;
import com.padeladmin.padeladmin.enums.MatchPhase;
import com.padeladmin.padeladmin.enums.MatchStatus;
import com.padeladmin.padeladmin.exception.ResourceNotFoundException;
import com.padeladmin.padeladmin.repository.MatchRepository;
import com.padeladmin.padeladmin.repository.MatchResultRepository;
import com.padeladmin.padeladmin.repository.PairPlayerRepository;
import com.padeladmin.padeladmin.repository.PlayerRepository;
import com.padeladmin.padeladmin.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computa estadísticas históricas de un jugador agregando datos de torneos, parejas,
 * partidos, sets y games. Una sola "pasada" por jugador — sin tablas extra.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerStatsService {

    private final PlayerRepository playerRepository;
    private final PairPlayerRepository pairPlayerRepository;
    private final MatchRepository matchRepository;
    private final MatchResultRepository matchResultRepository;
    private final TenantContext tenantContext;

    public PlayerStatsDto computeStats(Long playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador", playerId));

        // 1. Todas las parejas en las que jugó (con su tournament).
        //    Multi-tenant: un usuario CLUB solo ve la historia del jugador en SUS torneos.
        List<PairPlayer> myPairPlayers = pairPlayerRepository.findByPlayerId(playerId).stream()
                .filter(pp -> {
                    var club = pp.getPair().getTournament().getClub();
                    return tenantContext.canAccessClub(club != null ? club.getId() : null);
                })
                .toList();
        if (myPairPlayers.isEmpty()) {
            return emptyStats(player);
        }

        Set<Long> myPairIds = myPairPlayers.stream()
                .map(pp -> pp.getPair().getId())
                .collect(Collectors.toSet());

        // 2. Todos los matches donde participó alguna de esas parejas
        List<Match> allMatches = matchRepository.findByPairIdIn(new ArrayList<>(myPairIds));
        // Quedarme solo con los PLAYED para stats
        List<Match> playedMatches = allMatches.stream()
                .filter(m -> m.getStatus() == MatchStatus.PLAYED)
                .toList();

        // 3. Bulk-load de resultados
        Map<Long, MatchResult> resultByMatchId = new HashMap<>();
        for (Match m : playedMatches) {
            matchResultRepository.findByMatchId(m.getId()).ifPresent(r -> resultByMatchId.put(m.getId(), r));
        }

        // ── Contadores ───────────────────────────────────────────────────────
        int matchesWon = 0, matchesLost = 0;
        int walkoversReceived = 0, walkoversGiven = 0;
        int setsWon = 0, setsLost = 0;
        int gamesWon = 0, gamesLost = 0;

        // Por torneo: mejor instancia + matches jugados/ganados
        Map<Long, TournamentBucket> bucketsByTournament = new HashMap<>();

        // Por compañero: stats
        Map<Long, PartnerBucket> bucketsByPartner = new HashMap<>();

        for (Match m : playedMatches) {
            MatchResult res = resultByMatchId.get(m.getId());
            if (res == null) continue;

            Long myPairId = m.getPair1() != null && myPairIds.contains(m.getPair1().getId())
                    ? m.getPair1().getId()
                    : (m.getPair2() != null && myPairIds.contains(m.getPair2().getId()) ? m.getPair2().getId() : null);
            if (myPairId == null) continue;

            boolean iWasPair1 = m.getPair1() != null && m.getPair1().getId().equals(myPairId);
            boolean iWon = res.getWinnerPair() != null && res.getWinnerPair().getId().equals(myPairId);

            // Walkovers
            if (res.isWalkover()) {
                if (res.getWalkoverId() != null && res.getWalkoverId().equals(myPairId)) walkoversGiven++;
                else walkoversReceived++;
            }

            // Partidos
            if (iWon) matchesWon++; else matchesLost++;

            // Sets y games
            for (MatchSet set : res.getSets()) {
                int myGames = iWasPair1 ? set.getPair1Games() : set.getPair2Games();
                int otherGames = iWasPair1 ? set.getPair2Games() : set.getPair1Games();
                gamesWon += myGames;
                gamesLost += otherGames;
                if (myGames > otherGames) setsWon++;
                else if (otherGames > myGames) setsLost++;
            }

            // Bucket de torneo
            Long tId = m.getTournament().getId();
            TournamentBucket tb = bucketsByTournament.computeIfAbsent(tId, k -> new TournamentBucket(m.getTournament()));
            tb.matches++;
            if (iWon) tb.wins++;
            tb.updateBestStage(m, iWon);
            tb.addPair(myPairId);
        }

        // Compañeros: recorrer mis pairs y, para cada uno, los otros PairPlayer
        for (PairPlayer mine : myPairPlayers) {
            Long pairId = mine.getPair().getId();
            Long tId = mine.getPair().getTournament().getId();
            List<PairPlayer> partners = pairPlayerRepository.findByPairId(pairId).stream()
                    .filter(pp -> !pp.getPlayer().getId().equals(playerId))
                    .toList();
            for (PairPlayer pp : partners) {
                Long partnerId = pp.getPlayer().getId();
                PartnerBucket pb = bucketsByPartner.computeIfAbsent(partnerId,
                        k -> new PartnerBucket(pp.getPlayer()));
                pb.tournaments.add(tId);
            }
        }
        // Agregar matches/wins por partner — un partido se "comparte" si misma pair
        for (Match m : playedMatches) {
            MatchResult res = resultByMatchId.get(m.getId());
            if (res == null) continue;
            Long myPairId = m.getPair1() != null && myPairIds.contains(m.getPair1().getId())
                    ? m.getPair1().getId()
                    : (m.getPair2() != null && myPairIds.contains(m.getPair2().getId()) ? m.getPair2().getId() : null);
            if (myPairId == null) continue;
            boolean iWon = res.getWinnerPair() != null && res.getWinnerPair().getId().equals(myPairId);
            // Sumar al/los partner(s) de esa pair
            for (PairPlayer pp : pairPlayerRepository.findByPairId(myPairId)) {
                if (pp.getPlayer().getId().equals(playerId)) continue;
                PartnerBucket pb = bucketsByPartner.get(pp.getPlayer().getId());
                if (pb != null) {
                    pb.matches++;
                    if (iWon) pb.wins++;
                }
            }
        }

        // ── Construir DTOs ───────────────────────────────────────────────────
        int tournamentsPlayed = bucketsByTournament.size();
        int tournamentsWon = 0, tournamentsFinalist = 0, tournamentsSemi = 0;
        for (TournamentBucket tb : bucketsByTournament.values()) {
            switch (tb.bestStage) {
                case "CHAMPION" -> tournamentsWon++;
                case "FINALIST" -> tournamentsFinalist++;
                case "SEMIFINAL" -> tournamentsSemi++;
                default -> {}
            }
        }

        List<TournamentParticipationDto> history = bucketsByTournament.values().stream()
                .map(tb -> {
                    // Determinar nombre del compañero de ese torneo
                    String partnerName = tb.pairsUsed.stream()
                            .flatMap(pid -> pairPlayerRepository.findByPairId(pid).stream())
                            .filter(pp -> !pp.getPlayer().getId().equals(playerId))
                            .map(pp -> pp.getPlayer().getLastName() + ", " + pp.getPlayer().getFirstName())
                            .distinct()
                            .collect(Collectors.joining(" / "));
                    return TournamentParticipationDto.builder()
                            .tournamentId(tb.tournament.getId())
                            .tournamentName(tb.tournament.getName())
                            .categoryName(tb.tournament.getCategory() != null ? tb.tournament.getCategory().getName() : null)
                            .startDate(tb.tournament.getStartDate())
                            .tournamentStatus(tb.tournament.getStatus().name())
                            .bestStage(tb.bestStage)
                            .partnerName(partnerName.isBlank() ? "—" : partnerName)
                            .matchesPlayed(tb.matches)
                            .matchesWon(tb.wins)
                            .build();
                })
                .sorted(Comparator.comparing(TournamentParticipationDto::getStartDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<PartnerStatDto> topPartners = bucketsByPartner.values().stream()
                .map(pb -> PartnerStatDto.builder()
                        .partnerId(pb.player.getId())
                        .partnerName(pb.player.getLastName() + ", " + pb.player.getFirstName())
                        .tournamentsTogether(pb.tournaments.size())
                        .matchesTogether(pb.matches)
                        .matchesWon(pb.wins)
                        .build())
                .sorted(Comparator
                        .comparingInt(PartnerStatDto::getMatchesTogether).reversed()
                        .thenComparing(PartnerStatDto::getPartnerName))
                .limit(5)
                .toList();

        return PlayerStatsDto.builder()
                .playerId(player.getId())
                .firstName(player.getFirstName())
                .lastName(player.getLastName())
                .tournamentsPlayed(tournamentsPlayed)
                .tournamentsWon(tournamentsWon)
                .tournamentsFinalist(tournamentsFinalist)
                .tournamentsSemifinalist(tournamentsSemi)
                .matchesPlayed(matchesWon + matchesLost)
                .matchesWon(matchesWon)
                .matchesLost(matchesLost)
                .walkoversReceived(walkoversReceived)
                .walkoversGiven(walkoversGiven)
                .setsPlayed(setsWon + setsLost)
                .setsWon(setsWon)
                .setsLost(setsLost)
                .gamesPlayed(gamesWon + gamesLost)
                .gamesWon(gamesWon)
                .gamesLost(gamesLost)
                .topPartners(topPartners)
                .tournamentHistory(history)
                .build();
    }

    private PlayerStatsDto emptyStats(Player player) {
        return PlayerStatsDto.builder()
                .playerId(player.getId())
                .firstName(player.getFirstName())
                .lastName(player.getLastName())
                .topPartners(List.of())
                .tournamentHistory(List.of())
                .build();
    }

    // ── Helpers internos ─────────────────────────────────────────────────────

    /** Ranking de stages: menor número = más avance. */
    private static final Map<String, Integer> STAGE_RANK = Map.ofEntries(
            Map.entry("CHAMPION", 0),
            Map.entry("FINALIST", 1),
            Map.entry("SEMIFINAL", 2),
            Map.entry("QUARTERFINAL", 3),
            Map.entry("ROUND_8", 4),
            Map.entry("ROUND_16", 5),
            Map.entry("ROUND_32", 6),
            Map.entry("ZONE", 7),
            Map.entry("PARTICIPANT", 8)
    );

    private static class TournamentBucket {
        final Tournament tournament;
        int matches = 0;
        int wins = 0;
        String bestStage = "PARTICIPANT";
        final Set<Long> pairsUsed = new HashSet<>();

        TournamentBucket(Tournament t) { this.tournament = t; }

        void addPair(Long pairId) { pairsUsed.add(pairId); }

        void updateBestStage(Match m, boolean iWon) {
            String candidate;
            if (m.getPhase() == MatchPhase.ZONE) {
                candidate = "ZONE";
            } else {
                // ELIMINATION: derivar etapa según round y resultado
                Integer round = m.getEliminationRound();
                if (round == null) candidate = "PARTICIPANT";
                else if (round == 1) candidate = iWon ? "CHAMPION" : "FINALIST";
                else if (round == 2) candidate = "SEMIFINAL"; // si llegó a jugar semi, perdió ahí (si ganó pasaría a final)
                else if (round == 4) candidate = "QUARTERFINAL";
                else if (round == 8) candidate = "ROUND_8";
                else if (round == 16) candidate = "ROUND_16";
                else if (round == 32) candidate = "ROUND_32";
                else candidate = "PARTICIPANT";

                // Si jugó un round más alto y ganó, le tenemos que reconocer haber pasado:
                // ej: jugó cuartos (round=4) y ganó → llegó al menos a semis.
                if (iWon && round != null && round > 1) {
                    int nextRound = round / 2;
                    String advanced;
                    if (nextRound == 1) advanced = "FINALIST";       // ganó semis → llegó a final
                    else if (nextRound == 2) advanced = "SEMIFINAL"; // ganó cuartos → llegó a semis
                    else if (nextRound == 4) advanced = "QUARTERFINAL";
                    else if (nextRound == 8) advanced = "ROUND_8";
                    else if (nextRound == 16) advanced = "ROUND_16";
                    else advanced = candidate;
                    if (STAGE_RANK.get(advanced) < STAGE_RANK.get(candidate)) {
                        candidate = advanced;
                    }
                }
            }
            if (STAGE_RANK.get(candidate) < STAGE_RANK.get(bestStage)) {
                bestStage = candidate;
            }
        }
    }

    private static class PartnerBucket {
        final Player player;
        final Set<Long> tournaments = new HashSet<>();
        int matches = 0;
        int wins = 0;
        PartnerBucket(Player player) { this.player = player; }
    }
}
