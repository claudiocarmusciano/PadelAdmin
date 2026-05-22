package com.padeladmin.padeladmin.service;

import com.padeladmin.padeladmin.dto.elimination.EliminationBracketDto;
import com.padeladmin.padeladmin.dto.elimination.EliminationMatchDto;
import com.padeladmin.padeladmin.dto.fixture.MatchPairDto;
import com.padeladmin.padeladmin.entity.*;
import com.padeladmin.padeladmin.enums.MatchPhase;
import com.padeladmin.padeladmin.enums.MatchStatus;
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
public class EliminationService {

    private final TournamentRepository tournamentRepository;
    private final ZoneRepository zoneRepository;
    private final ZonePairRepository zonePairRepository;
    private final MatchRepository matchRepository;
    private final MatchResultRepository matchResultRepository;
    private final PairRepository pairRepository;
    private final PairPlayerRepository pairPlayerRepository;

    // ── Generar bracket eliminatorio ──────────────────────────────────────────

    @Transactional
    public EliminationBracketDto generateBracket(Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", tournamentId));

        // 1. Recolectar pares clasificados (valida que todas las zonas estén completas)
        List<ClassifiedPairInfo> classified = getClassifiedPairs(tournamentId);
        int n = classified.size();
        if (n < 2) throw new BusinessException("Se necesitan al menos 2 parejas clasificadas");

        // 2. Eliminar bracket existente
        List<Match> existing = matchRepository.findByTournamentIdAndPhase(tournamentId, MatchPhase.ELIMINATION);
        matchRepository.deleteAll(existing);

        // 3. Tamaño del bracket (siguiente potencia de 2)
        int bracketSize = nextPowerOf2(n);
        int numByes = bracketSize - n;

        // 4. Orden de seeds en el bracket (template estándar)
        List<Integer> seedOrder = generateSeedOrder(bracketSize);

        // 5. Mapa seed→pareja (seeds 1..n = clasificados, seeds n+1..bracketSize = BYE)
        List<ClassifiedPairInfo> seedMap = new ArrayList<>(classified);
        for (int i = 0; i < numByes; i++) seedMap.add(null); // null = BYE

        // 6. Resolver conflictos de zona en ronda 1
        resolveZoneConflicts(seedOrder, seedMap);

        // 7. Crear partidos de primera ronda
        int firstRound = bracketSize; // 8=QF en bracket de 8, etc.
        // Ajustamos: eliminationRound = bracketSize/2 para la primera ronda jugada
        // (si bracketSize=8 → primera ronda eliminationRound=4 = cuartos)
        int firstEliminationRound = bracketSize / 2;

        List<Match> firstRoundMatches = new ArrayList<>();
        for (int i = 0; i < seedOrder.size(); i += 2) {
            int seed1 = seedOrder.get(i);
            int seed2 = seedOrder.get(i + 1);
            int slot = i / 2 + 1;

            ClassifiedPairInfo info1 = seedMap.get(seed1 - 1);
            ClassifiedPairInfo info2 = seedMap.get(seed2 - 1);

            boolean isBye = (info1 == null || info2 == null);

            Pair pair1 = info1 != null
                    ? pairRepository.findById(info1.pairId()).orElseThrow()
                    : null;
            Pair pair2 = info2 != null
                    ? pairRepository.findById(info2.pairId()).orElseThrow()
                    : null;

            // En partidos con BYE, pair1 = la pareja real, pair2 = null
            if (isBye && pair1 == null) { pair1 = pair2; pair2 = null; }

            Match match = Match.builder()
                    .tournament(tournament)
                    .phase(MatchPhase.ELIMINATION)
                    .eliminationRound(firstEliminationRound)
                    .bracketSlot(slot)
                    .pair1(pair1)
                    .pair2(pair2)
                    .bye(isBye)
                    .status(isBye ? MatchStatus.PLAYED : MatchStatus.PENDING)
                    .build();

            firstRoundMatches.add(match);
        }
        matchRepository.saveAll(firstRoundMatches);

        // 8. Crear placeholders para rondas siguientes (pair1/pair2 null = TBD)
        createSubsequentRoundPlaceholders(tournament, bracketSize, firstEliminationRound);

        // 9. Avanzar automáticamente las parejas con BYE en primera ronda
        for (Match m : firstRoundMatches) {
            if (m.isBye() && m.getPair1() != null) {
                advanceWinner(m, m.getPair1());
            }
        }

        return getBracket(tournamentId);
    }

    // ── Avanzar ganador al siguiente match ────────────────────────────────────

    @Transactional
    public void advanceWinner(Match match, Pair winner) {
        if (match.getEliminationRound() == null || match.getEliminationRound() == 1) {
            // Es la Final → no hay siguiente ronda
            log.info("¡Campeón! Pareja {} ganó la Final", winner.getId());
            return;
        }

        int nextRound = match.getEliminationRound() / 2;
        int nextSlot = (match.getBracketSlot() + 1) / 2;
        // Si bracketSlot es impar → el ganador ocupa pair1 en el siguiente match
        // Si es par → ocupa pair2
        boolean isPair1 = match.getBracketSlot() % 2 == 1;

        Match nextMatch = matchRepository
                .findByTournamentIdAndPhaseAndEliminationRoundAndBracketSlot(
                        match.getTournament().getId(), MatchPhase.ELIMINATION, nextRound, nextSlot)
                .orElseThrow(() -> new BusinessException(
                        "No se encontró el partido siguiente en el bracket (ronda " + nextRound + ", slot " + nextSlot + ")"));

        if (isPair1) {
            nextMatch.setPair1(winner);
        } else {
            nextMatch.setPair2(winner);
        }

        // Si ya están las dos parejas del siguiente partido, se puede programar
        if (nextMatch.getPair1() != null && nextMatch.getPair2() != null) {
            nextMatch.setStatus(MatchStatus.PENDING);
            log.info("Partido listo para programar: ronda {} slot {}", nextRound, nextSlot);
        }

        matchRepository.save(nextMatch);
    }

    // ── Ver bracket actual ────────────────────────────────────────────────────

    public EliminationBracketDto getBracket(Long tournamentId) {
        tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", tournamentId));

        List<Match> matches = matchRepository
                .findByTournamentIdAndPhase(tournamentId, MatchPhase.ELIMINATION);

        if (matches.isEmpty()) {
            throw new BusinessException("El bracket eliminatorio aún no fue generado");
        }

        int bracketSize = matches.stream()
                .mapToInt(Match::getEliminationRound)
                .max().orElse(1) * 2;

        // Clasificar total = cantidad de matches en primera ronda * 2 (menos byes)
        long totalClassified = matches.stream()
                .filter(m -> m.getEliminationRound().equals(bracketSize / 2))
                .mapToLong(m -> (m.getPair1() != null ? 1 : 0) + (m.getPair2() != null ? 1 : 0))
                .sum();

        Map<Integer, List<EliminationMatchDto>> rounds = matches.stream()
                .sorted(Comparator.comparingInt(Match::getBracketSlot))
                .collect(Collectors.groupingBy(
                        Match::getEliminationRound,
                        TreeMap::new,
                        Collectors.mapping(this::toMatchDto, Collectors.toList())
                ));

        // Invertir el orden: de mayor eliminationRound (R16) a menor (Final)
        Map<Integer, List<EliminationMatchDto>> orderedRounds = new TreeMap<>(Comparator.reverseOrder());
        orderedRounds.putAll(rounds);

        return EliminationBracketDto.builder()
                .tournamentId(tournamentId)
                .totalClassified((int) totalClassified)
                .bracketSize(bracketSize)
                .rounds(orderedRounds)
                .build();
    }

    // ── Clasificados por zona ─────────────────────────────────────────────────

    private List<ClassifiedPairInfo> getClassifiedPairs(Long tournamentId) {
        List<Zone> zones = zoneRepository.findByTournamentIdOrderByZoneOrder(tournamentId);
        if (zones.isEmpty()) throw new BusinessException("El torneo no tiene zonas generadas");

        List<ClassifiedPairInfo> group1 = new ArrayList<>(); // primeros de zona
        List<ClassifiedPairInfo> group2 = new ArrayList<>(); // segundos de zona
        List<ClassifiedPairInfo> group3 = new ArrayList<>(); // terceros (solo zona de 4)

        for (Zone zone : zones) {
            validateAllMatchesPlayed(zone);

            List<ClassifiedPairInfo> zoneClassified;
            if (zone.getZoneSize() == 4) {
                zoneClassified = getZoneOf4Classified(zone);
            } else {
                zoneClassified = getZoneOf3Classified(zone);
            }

            if (zoneClassified.size() > 0) group1.add(zoneClassified.get(0));
            if (zoneClassified.size() > 1) group2.add(zoneClassified.get(1));
            if (zoneClassified.size() > 2) group3.add(zoneClassified.get(2));
        }

        List<ClassifiedPairInfo> all = new ArrayList<>();
        all.addAll(group1);
        all.addAll(group2);
        all.addAll(group3);
        return all;
    }

    private void validateAllMatchesPlayed(Zone zone) {
        List<Match> matches = matchRepository.findByZoneId(zone.getId());
        boolean anyPending = matches.stream()
                .anyMatch(m -> m.getStatus() != MatchStatus.PLAYED);
        if (anyPending) {
            throw new BusinessException(
                    "La Zona " + zone.getName() + " tiene partidos sin resultado registrado. " +
                    "Todos los partidos deben estar jugados antes de generar la eliminatoria.");
        }
    }

    /** Clasificados de zona de 3: top 2 por victorias → dif. sets → dif. games */
    private List<ClassifiedPairInfo> getZoneOf3Classified(Zone zone) {
        List<ZonePair> zonePairs = zonePairRepository.findByZoneIdOrderByPosition(zone.getId());
        List<Match> matches = matchRepository.findByZoneId(zone.getId());

        Map<Long, int[]> stats = new HashMap<>(); // [wins, setsFor, setsAgainst, gamesFor, gamesAgainst]
        for (ZonePair zp : zonePairs) stats.put(zp.getPair().getId(), new int[5]);

        for (Match m : matches) {
            if (m.getStatus() != MatchStatus.PLAYED) continue;
            MatchResult r = matchResultRepository.findByMatchId(m.getId()).orElseThrow();
            Long p1 = m.getPair1().getId(), p2 = m.getPair2().getId();
            Long winner = r.getWinnerPair().getId();
            int[] s1 = stats.get(p1), s2 = stats.get(p2);
            s1[1] += r.getPair1Score(); s1[2] += r.getPair2Score();
            s2[1] += r.getPair2Score(); s2[2] += r.getPair1Score();
            for (MatchSet ms : r.getSets()) {
                s1[3] += ms.getPair1Games(); s1[4] += ms.getPair2Games();
                s2[3] += ms.getPair2Games(); s2[4] += ms.getPair1Games();
            }
            if (winner.equals(p1)) { s1[0]++; } else { s2[0]++; }
        }

        List<ZonePair> sorted = new ArrayList<>(zonePairs);
        sorted.sort((a, b) -> {
            int[] sa = stats.get(a.getPair().getId()), sb = stats.get(b.getPair().getId());
            if (sa[0] != sb[0]) return sb[0] - sa[0];
            int diffA = sa[1] - sa[2], diffB = sb[1] - sb[2];
            if (diffA != diffB) return diffB - diffA;
            return (sb[3] - sb[4]) - (sa[3] - sa[4]);
        });

        return List.of(
                new ClassifiedPairInfo(sorted.get(0).getPair().getId(), zone.getId(), zone.getName(), 1),
                new ClassifiedPairInfo(sorted.get(1).getPair().getId(), zone.getId(), zone.getName(), 2)
        );
    }

    /** Clasificados de zona de 4: 1°=ganador partido ganadores, 2°=perdedor, 3°=ganador partido perdedores */
    private List<ClassifiedPairInfo> getZoneOf4Classified(Zone zone) {
        List<Match> round1 = matchRepository.findByZoneIdAndZoneRound(zone.getId(), 1);
        List<Match> round2 = matchRepository.findByZoneIdAndZoneRound(zone.getId(), 2);

        // Identificar cuál es el partido de ganadores y cuál el de perdedores
        Set<Long> round1Winners = round1.stream()
                .map(m -> matchResultRepository.findByMatchId(m.getId()).orElseThrow())
                .map(r -> r.getWinnerPair().getId())
                .collect(Collectors.toSet());

        Match winnersMatch = round2.stream()
                .filter(m -> round1Winners.contains(m.getPair1().getId())
                          && round1Winners.contains(m.getPair2().getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("No se encontró el partido de ganadores en Zona " + zone.getName()));

        Match losersMatch = round2.stream()
                .filter(m -> !m.getId().equals(winnersMatch.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("No se encontró el partido de perdedores en Zona " + zone.getName()));

        MatchResult wr = matchResultRepository.findByMatchId(winnersMatch.getId()).orElseThrow();
        MatchResult lr = matchResultRepository.findByMatchId(losersMatch.getId()).orElseThrow();

        Pair first = wr.getWinnerPair();
        Pair second = wr.getWinnerPair().getId().equals(winnersMatch.getPair1().getId())
                ? winnersMatch.getPair2() : winnersMatch.getPair1();
        Pair third = lr.getWinnerPair();

        return List.of(
                new ClassifiedPairInfo(first.getId(), zone.getId(), zone.getName(), 1),
                new ClassifiedPairInfo(second.getId(), zone.getId(), zone.getName(), 2),
                new ClassifiedPairInfo(third.getId(), zone.getId(), zone.getName(), 3)
        );
    }

    // ── Algoritmo de bracket ──────────────────────────────────────────────────

    /**
     * Genera el orden de seeds en el bracket.
     * Para n=8 → [1, 8, 4, 5, 2, 7, 3, 6]
     * Matches: (1,8), (4,5), (2,7), (3,6) — los mejores seeds están en lados opuestos.
     */
    private List<Integer> generateSeedOrder(int n) {
        if (n == 2) return new ArrayList<>(Arrays.asList(1, 2));
        List<Integer> half = generateSeedOrder(n / 2);
        List<Integer> result = new ArrayList<>();
        for (int seed : half) {
            result.add(seed);
            result.add(n + 1 - seed);
        }
        return result;
    }

    /**
     * Resuelve conflictos de zona en la ronda 1 del bracket.
     * Si dos pares del mismo grupo (mismo zonePosition) y de la misma zona
     * se enfrentan en ronda 1, intercambia uno con otro del mismo grupo
     * que no genere nuevos conflictos.
     */
    private void resolveZoneConflicts(List<Integer> seedOrder, List<ClassifiedPairInfo> seedMap) {
        // Detectar y resolver hasta 3 pasadas (por si hay múltiples conflictos)
        for (int pass = 0; pass < 3; pass++) {
            boolean anyResolved = false;
            for (int i = 0; i < seedOrder.size() - 1; i += 2) {
                int seedA = seedOrder.get(i);
                int seedB = seedOrder.get(i + 1);
                ClassifiedPairInfo pairA = seedMap.get(seedA - 1);
                ClassifiedPairInfo pairB = seedMap.get(seedB - 1);

                if (pairA == null || pairB == null) continue; // BYE, no hay conflicto
                if (!pairA.zoneId().equals(pairB.zoneId())) continue; // sin conflicto

                // Conflicto: pairA y pairB son del mismo grupo. Intentar swap de pairB
                int conflictGroup = pairB.zonePosition();
                boolean resolved = false;

                for (int j = 0; j < seedMap.size(); j++) {
                    if (j == seedA - 1 || j == seedB - 1) continue;
                    ClassifiedPairInfo candidate = seedMap.get(j);
                    if (candidate == null) continue;
                    if (candidate.zonePosition() != conflictGroup) continue;
                    if (candidate.zoneId().equals(pairA.zoneId())) continue; // seguiría conflicto

                    // Verificar que el candidato no crea nuevo conflicto con su nuevo partner
                    int candidatePartnerSeed = getRound1Partner(j + 1, seedOrder);
                    ClassifiedPairInfo candidatePartner = candidatePartnerSeed > 0
                            ? seedMap.get(candidatePartnerSeed - 1) : null;

                    boolean noConflictWithA = !pairA.zoneId().equals(candidate.zoneId());
                    boolean noConflictWithPartner = candidatePartner == null
                            || !candidatePartner.zoneId().equals(pairB.zoneId());

                    if (noConflictWithA && noConflictWithPartner) {
                        seedMap.set(seedB - 1, candidate);
                        seedMap.set(j, pairB);
                        log.info("Swap en bracket: seed {} ({}) ↔ seed {} ({})",
                                seedB, pairB.zoneName(), j + 1, candidate.zoneName());
                        resolved = true;
                        anyResolved = true;
                        break;
                    }
                }

                if (!resolved) {
                    log.warn("No se pudo resolver conflicto de zona en ronda 1: {} vs {}",
                            pairA.zoneName(), pairB.zoneName());
                }
            }
            if (!anyResolved) break;
        }
    }

    private int getRound1Partner(int seed, List<Integer> seedOrder) {
        for (int i = 0; i < seedOrder.size() - 1; i += 2) {
            if (seedOrder.get(i) == seed) return seedOrder.get(i + 1);
            if (seedOrder.get(i + 1) == seed) return seedOrder.get(i);
        }
        return -1;
    }

    /** Crea los partidos placeholder de las rondas siguientes a la primera (pair1/pair2 = TBD). */
    private void createSubsequentRoundPlaceholders(Tournament tournament, int bracketSize, int firstEliminationRound) {
        int round = firstEliminationRound / 2;
        int slots = firstEliminationRound / 2; // la siguiente ronda tiene la mitad de partidos

        while (round >= 1) {
            for (int slot = 1; slot <= slots; slot++) {
                String roundName = roundName(round);
                Match placeholder = Match.builder()
                        .tournament(tournament)
                        .phase(MatchPhase.ELIMINATION)
                        .eliminationRound(round)
                        .bracketSlot(slot)
                        .bye(false)
                        .status(MatchStatus.PENDING)
                        .build();
                matchRepository.save(placeholder);
            }
            slots = slots / 2;
            round = round / 2;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int nextPowerOf2(int n) {
        int p = 1;
        while (p < n) p *= 2;
        return p;
    }

    public static String roundName(int eliminationRound) {
        return switch (eliminationRound) {
            case 1 -> "Final";
            case 2 -> "Semifinales";
            case 4 -> "Cuartos de Final";
            case 8 -> "Octavos de Final";
            case 16 -> "Dieciseisavos de Final";
            default -> "Ronda de " + (eliminationRound * 2);
        };
    }

    // ── Mapeo a DTO ───────────────────────────────────────────────────────────

    private EliminationMatchDto toMatchDto(Match match) {
        return EliminationMatchDto.builder()
                .id(match.getId())
                .eliminationRound(match.getEliminationRound())
                .roundName(roundName(match.getEliminationRound()))
                .bracketSlot(match.getBracketSlot())
                .pair1(match.getPair1() != null ? toPairDto(match.getPair1()) : null)
                .pair2(match.getPair2() != null ? toPairDto(match.getPair2()) : null)
                .bye(match.isBye())
                .courtName(match.getCourt() != null ? match.getCourt().getName() : null)
                .scheduledStart(match.getScheduledStart())
                .scheduledEnd(match.getScheduledEnd())
                .status(match.getStatus())
                .build();
    }

    private MatchPairDto toPairDto(Pair pair) {
        List<PairPlayer> players = pairPlayerRepository.findByPairId(pair.getId());
        String p1 = players.size() > 0
                ? players.get(0).getPlayer().getLastName() + " / " + players.get(0).getPlayer().getFirstName()
                : "-";
        String p2 = players.size() > 1
                ? players.get(1).getPlayer().getLastName() + " / " + players.get(1).getPlayer().getFirstName()
                : "-";
        return MatchPairDto.builder()
                .id(pair.getId())
                .player1(p1)
                .player2(p2)
                .totalPoints(pair.getTotalPoints())
                .build();
    }

    // ── Record interno ────────────────────────────────────────────────────────

    record ClassifiedPairInfo(Long pairId, Long zoneId, String zoneName, int zonePosition) {}
}
