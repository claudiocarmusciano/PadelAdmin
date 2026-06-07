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

        // 2. Eliminar bracket existente.
        //    Primero los resultados de esos partidos (la FK match_result→match lo exige; los sets
        //    se borran por cascade), si no, deleteAll falla con error de integridad.
        List<Match> existing = matchRepository.findByTournamentIdAndPhase(tournamentId, MatchPhase.ELIMINATION);
        for (Match m : existing) {
            matchResultRepository.findByMatchId(m.getId()).ifPresent(matchResultRepository::delete);
        }
        matchResultRepository.flush();
        matchRepository.deleteAll(existing);

        // 3. Tamaño del bracket: nextPowerOf2 del número de CLASIFICADOS.
        //    (El template oficial a usar se elige en base al total de INSCRIPTOS.)
        int totalPairs = (int) pairRepository.countByTournamentId(tournamentId);
        int bracketSize = nextPowerOf2(n);
        int numByes = bracketSize - n;
        log.info("Torneo {}: {} inscriptos, {} clasificados → bracket de {}", tournamentId, totalPairs, n, bracketSize);

        // 4. Orden de seeds: template oficial seleccionado por cantidad de INSCRIPTOS
        List<Integer> seedOrder = generateSeedOrder(n, bracketSize, totalPairs);

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

    @Transactional
    public EliminationBracketDto getBracket(Long tournamentId) {
        tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", tournamentId));

        List<Match> matches = matchRepository
                .findByTournamentIdAndPhase(tournamentId, MatchPhase.ELIMINATION);

        if (matches.isEmpty()) {
            // Si TODAS las zonas ya están jugadas, armamos el bracket real automáticamente
            // (con los nombres de las parejas clasificadas y la opción de cargar resultados).
            if (allZonesComplete(tournamentId)) {
                return generateBracket(tournamentId);
            }
            // Si todavía faltan resultados de zona, devolvemos la VISTA PREVIA estructural.
            return getBracketPreview(tournamentId);
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
                .preview(false)
                .stale(isBracketStale(tournamentId, matches, bracketSize))
                .rounds(orderedRounds)
                .build();
    }

    // ── Vista previa del bracket (sin resultados de zona) ──────────────────────

    /**
     * Arma el cuadro eliminatorio de forma TENTATIVA, antes de que las zonas tengan resultados.
     * No persiste nada: usa "clasificados virtuales" (zona + posición) y reutiliza exactamente la
     * misma lógica de seeding (templates FAP + resolución de conflictos de zona) que la generación real.
     *
     * Cada cruce se rotula por posición:
     *   - 1ª ronda: "1º Zona A" vs "2º Zona D", "BYE", etc.
     *   - rondas siguientes: "Ganador" (propagando el seed concreto cuando viene de un BYE).
     */
    public EliminationBracketDto getBracketPreview(Long tournamentId) {
        tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", tournamentId));

        List<Zone> zones = zoneRepository.findByTournamentIdOrderByZoneOrder(tournamentId);
        if (zones.isEmpty()) {
            throw new BusinessException("El torneo no tiene zonas generadas todavía.");
        }

        // Clasificados "virtuales" en el MISMO orden que getClassifiedPairs:
        // todos los 1º (en orden de zona), luego todos los 2º, luego los 3º (solo zonas de 4)
        List<ClassifiedPairInfo> virtual = new ArrayList<>();
        for (Zone z : zones) virtual.add(new ClassifiedPairInfo(null, z.getId(), z.getName(), 1));
        for (Zone z : zones) virtual.add(new ClassifiedPairInfo(null, z.getId(), z.getName(), 2));
        for (Zone z : zones) if (z.getZoneSize() == 4) virtual.add(new ClassifiedPairInfo(null, z.getId(), z.getName(), 3));

        int n = virtual.size();
        if (n < 2) throw new BusinessException("Se necesitan al menos 2 parejas para armar el cuadro.");

        int totalPairs = (int) pairRepository.countByTournamentId(tournamentId);
        int bracketSize = nextPowerOf2(n);
        int numByes = bracketSize - n;

        // Mismo seeding y resolución de conflictos que el bracket real (todo opera sobre zona+posición)
        List<Integer> seedOrder = generateSeedOrder(n, bracketSize, totalPairs);
        List<ClassifiedPairInfo> seedMap = new ArrayList<>(virtual);
        for (int i = 0; i < numByes; i++) seedMap.add(null); // BYE
        resolveZoneConflicts(seedOrder, seedMap);

        int firstElim = bracketSize / 2;

        // Por ronda: cada match es [topLabel, botLabel, advancingLabel, bye("1"/"0")]
        Map<Integer, List<String[]>> byRound = new HashMap<>();

        // Primera ronda jugada (a partir del seedOrder)
        List<String[]> firstRound = new ArrayList<>();
        for (int i = 0; i < seedOrder.size(); i += 2) {
            ClassifiedPairInfo a = seedMap.get(seedOrder.get(i) - 1);
            ClassifiedPairInfo b = seedMap.get(seedOrder.get(i + 1) - 1);
            boolean bye = (a == null || b == null);
            if (bye) {
                // En un BYE, la pareja real pasa directo → la mostramos arriba (pair1)
                String real = (a != null) ? posLabel(a) : posLabel(b);
                firstRound.add(new String[]{ real, "BYE", real, "1" });
            } else {
                firstRound.add(new String[]{ posLabel(a), posLabel(b), "Ganador", "0" });
            }
        }
        byRound.put(firstElim, firstRound);

        // Rondas siguientes: cada slot recibe el "advancingLabel" de sus dos alimentadores
        int round = firstElim / 2;
        while (round >= 1) {
            List<String[]> prev = byRound.get(round * 2);
            List<String[]> cur = new ArrayList<>();
            for (int s = 0; s < prev.size() / 2; s++) {
                String top = prev.get(2 * s)[2];
                String bot = prev.get(2 * s + 1)[2];
                cur.add(new String[]{ top, bot, "Ganador", "0" });
            }
            byRound.put(round, cur);
            round /= 2;
        }

        // Armar DTOs (id sintético, no persistido)
        Map<Integer, List<EliminationMatchDto>> rounds = new TreeMap<>(Comparator.reverseOrder());
        for (Map.Entry<Integer, List<String[]>> e : byRound.entrySet()) {
            int r = e.getKey();
            List<EliminationMatchDto> dtos = new ArrayList<>();
            List<String[]> ms = e.getValue();
            for (int slot = 0; slot < ms.size(); slot++) {
                String[] m = ms.get(slot);
                dtos.add(EliminationMatchDto.builder()
                        .id((long) (r * 1000L + slot + 1))
                        .eliminationRound(r)
                        .roundName(roundName(r))
                        .bracketSlot(slot + 1)
                        .pair1Label(m[0])
                        .pair2Label(m[1])
                        .bye("1".equals(m[3]))
                        .status(MatchStatus.PENDING)
                        .build());
            }
            rounds.put(r, dtos);
        }

        return EliminationBracketDto.builder()
                .tournamentId(tournamentId)
                .totalClassified(n)
                .bracketSize(bracketSize)
                .preview(true)
                .rounds(rounds)
                .build();
    }

    /** Etiqueta de posición de un clasificado virtual: "1º Zona A". */
    private String posLabel(ClassifiedPairInfo info) {
        String ord = switch (info.zonePosition()) {
            case 1 -> "1º";
            case 2 -> "2º";
            case 3 -> "3º";
            default -> info.zonePosition() + "º";
        };
        return ord + " Zona " + info.zoneName();
    }

    /**
     * Si existe un bracket eliminatorio que TODAVÍA NO empezó (ningún partido real jugado),
     * lo borra para que se regenere con la clasificación corregida (ej: al editar un resultado
     * de zona). Si el bracket ya tiene resultados cargados, no se toca (no se pisa el progreso).
     */
    @Transactional
    public void invalidateBracketIfNotStarted(Long tournamentId) {
        List<Match> elim = matchRepository.findByTournamentIdAndPhase(tournamentId, MatchPhase.ELIMINATION);
        if (elim.isEmpty()) return;
        // Un partido con BYE se crea como PLAYED (avance automático): no cuenta como "empezado".
        boolean started = elim.stream().anyMatch(m -> m.getStatus() == MatchStatus.PLAYED && !m.isBye());
        if (started) {
            log.info("Bracket del torneo {} ya tiene resultados: no se regenera automáticamente", tournamentId);
            return;
        }
        matchRepository.deleteAll(elim);
        log.info("Bracket del torneo {} invalidado por cambio en resultados de zona (se regenerará al verlo)", tournamentId);
    }

    /**
     * Detecta si un bracket real ya generado quedó desactualizado respecto a la clasificación
     * ACTUAL de las zonas (ej: se corrigió un resultado de zona después de generar el cuadro).
     * Recalcula el sembrado de primera ronda como lo haría una regeneración y lo compara con
     * lo persistido; si algún cruce difiere, el bracket está desactualizado.
     */
    private boolean isBracketStale(Long tournamentId, List<Match> existing, int bracketSize) {
        List<ClassifiedPairInfo> classified;
        try {
            classified = getClassifiedPairs(tournamentId);
        } catch (BusinessException e) {
            return false; // zonas incompletas en este instante → no marcamos nada
        }
        int n = classified.size();
        if (nextPowerOf2(n) != bracketSize) return true; // cambió la cantidad de clasificados

        int totalPairs = (int) pairRepository.countByTournamentId(tournamentId);
        List<Integer> seedOrder = generateSeedOrder(n, bracketSize, totalPairs);
        List<ClassifiedPairInfo> seedMap = new ArrayList<>(classified);
        for (int i = 0; i < bracketSize - n; i++) seedMap.add(null);
        resolveZoneConflicts(seedOrder, seedMap);

        int firstElim = bracketSize / 2;
        // Cruces persistidos en primera ronda: slot -> set de pairIds
        Map<Integer, Set<Long>> actual = new HashMap<>();
        for (Match m : existing) {
            if (m.getEliminationRound() != null && m.getEliminationRound() == firstElim) {
                Set<Long> ids = new HashSet<>();
                if (m.getPair1() != null) ids.add(m.getPair1().getId());
                if (m.getPair2() != null) ids.add(m.getPair2().getId());
                actual.put(m.getBracketSlot(), ids);
            }
        }
        // Cruces esperados según la clasificación actual
        for (int i = 0; i < seedOrder.size(); i += 2) {
            int slot = i / 2 + 1;
            ClassifiedPairInfo a = seedMap.get(seedOrder.get(i) - 1);
            ClassifiedPairInfo b = seedMap.get(seedOrder.get(i + 1) - 1);
            Set<Long> expected = new HashSet<>();
            if (a != null) expected.add(a.pairId());
            if (b != null) expected.add(b.pairId());
            if (!expected.equals(actual.getOrDefault(slot, Set.of()))) return true;
        }
        return false;
    }

    /** true si el torneo tiene zonas y TODOS sus partidos de zona están jugados (PLAYED). */
    private boolean allZonesComplete(Long tournamentId) {
        List<Zone> zones = zoneRepository.findByTournamentIdOrderByZoneOrder(tournamentId);
        if (zones.isEmpty()) return false;
        for (Zone z : zones) {
            List<Match> zoneMatches = matchRepository.findByZoneId(z.getId());
            if (zoneMatches.isEmpty()) return false;
            if (zoneMatches.stream().anyMatch(m -> m.getStatus() != MatchStatus.PLAYED)) return false;
        }
        return true;
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

    /**
     * Clasificados de zona de 3 — desempate oficial:
     * 1) Victorias  2) Dif.sets  3) Dif.games  4) Games+  5) Games-  6) H2H  7) Sorteo
     */
    private List<ClassifiedPairInfo> getZoneOf3Classified(Zone zone) {
        List<ZonePair> zonePairs = zonePairRepository.findByZoneIdOrderByPosition(zone.getId());
        List<Match> matches = matchRepository.findByZoneId(zone.getId());

        Map<Long, int[]> stats = new HashMap<>(); // [wins, setsFor, setsAgainst, gamesFor, gamesAgainst]
        for (ZonePair zp : zonePairs) stats.put(zp.getPair().getId(), new int[5]);

        // Mapa cabeza a cabeza: key = "min-max pairId", value = pairId ganador
        Map<String, Long> h2hMap = new HashMap<>();

        for (Match m : matches) {
            if (m.getStatus() != MatchStatus.PLAYED) continue;
            MatchResult r = matchResultRepository.findByMatchId(m.getId()).orElseThrow();
            Long p1 = m.getPair1().getId(), p2 = m.getPair2().getId();
            Long winner = r.getWinnerPair().getId();

            String h2hKey = Math.min(p1, p2) + "-" + Math.max(p1, p2);
            h2hMap.put(h2hKey, winner);

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
            // 1. Victorias
            if (sa[0] != sb[0]) return sb[0] - sa[0];
            // 2. Diferencia de sets
            int setDiffA = sa[1] - sa[2], setDiffB = sb[1] - sb[2];
            if (setDiffA != setDiffB) return setDiffB - setDiffA;
            // 3. Diferencia de games
            int gameDiffA = sa[3] - sa[4], gameDiffB = sb[3] - sb[4];
            if (gameDiffA != gameDiffB) return gameDiffB - gameDiffA;
            // 4. Games a favor
            if (sa[3] != sb[3]) return sb[3] - sa[3];
            // 5. Games en contra (menos es mejor)
            if (sa[4] != sb[4]) return sa[4] - sb[4];
            // 6. Resultado en cancha (H2H)
            Long winner = h2hMap.get(Math.min(a.getPair().getId(), b.getPair().getId()) + "-"
                    + Math.max(a.getPair().getId(), b.getPair().getId()));
            if (winner == null) return 0;
            return winner.equals(a.getPair().getId()) ? -1 : 1;
            // 7. Sorteo — no automatizable
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
     * Genera el orden de enfrentamientos de la primera ronda según los templates oficiales
     * de la Federación Argentina de Pádel.
     *
     * - bracketSize = nextPowerOf2(numClassified): determina el tamaño del cuadro.
     * - totalPairs: cantidad de parejas INSCRIPTAS; se usa para elegir el template oficial
     *   correcto dentro de un mismo bracketSize (ej. para bracketSize=16 hay templates
     *   distintos según si el torneo tiene 13, 14, 15 ó 16 inscriptos).
     * - Seeds > numClassified son BYEs (posición null en seedMap).
     *
     * Retorna pares consecutivos: [s1, s2, s3, s4, ...] donde s1 juega contra s2, etc.
     *
     * Templates oficiales implementados (totalPairs inscriptos):
     *    6 → bracket 4:  1ºAvs2ºB | 2ºAvs1ºB
     *   (más templates se agregan a medida que se reciben de la FAP)
     */
    private List<Integer> generateSeedOrder(int numClassified, int bracketSize, int totalPairs) {
        // las seeds > numClassified son BYE; usamos numClassified+k como BYE slots
        int b1  = numClassified +  1, b2  = numClassified +  2,
            b3  = numClassified +  3, b4  = numClassified +  4,
            b5  = numClassified +  5, b6  = numClassified +  6,
            b7  = numClassified +  7, b8  = numClassified +  8,
            b9  = numClassified +  9, b10 = numClassified + 10,
            b11 = numClassified + 11, b12 = numClassified + 12,
            b13 = numClassified + 13, b14 = numClassified + 14,
            b15 = numClassified + 15;

        // La selección de template usa numClassified (estructura de zonas) dentro del bracketSize
        // ya determinado por totalPairs. Los BYE variables b1..b12 = numClassified+1..numClassified+12.

        if (bracketSize == 4) {
            // FAP — 6 inscriptos (2 zonas de 3 → 4 clasificados)
            // seeds: 1=1ºA, 2=1ºB, 3=2ºA, 4=2ºB
            // Partido 61: 1ºA vs 2ºB  |  Partido 62: 2ºA vs 1ºB  |  Final 64
            return new ArrayList<>(Arrays.asList(1, 4, 3, 2));
        }

        if (bracketSize == 8) {
            if (numClassified == 5) {
                // FAP — 7 inscriptos (1Z×4 + 1Z×3 → 5 clasificados, 3 BYEs)
                // seeds: 1=1ºA, 2=1ºB, 3=2ºA, 4=2ºB, 5=3ºA
                // QF: 1ºA(BYE) | 3ºAvs2ºB[58] | 2ºA(BYE) | 1ºB(BYE)
                // SF: 1ºAvs G(58)[61] | 2ºAvs1ºB[62]
                return new ArrayList<>(Arrays.asList(1, b1, 5, 4, 3, b2, b3, 2));
            } else if (numClassified == 6 && totalPairs == 8) {
                // FAP — 8 inscriptos (2Z×4 → 3+3=6 clasificados, 2 BYEs)
                // seeds: 1=1ºA, 2=1ºB, 3=2ºA, 4=2ºB, 5=3ºA, 6=3ºB
                // QF: 1ºA(BYE) | 3ºAvs2ºB[58] | 2ºAvs3ºB[59] | 1ºB(BYE)
                // SF: 1ºAvs G(58)[61] | G(59)vs1ºB[62]
                return new ArrayList<>(Arrays.asList(1, b1, 5, 4, 3, 6, b2, 2));
            } else if (numClassified == 6 && totalPairs == 9) {
                // FAP — 9 inscriptos (3Z×3 → 2+2+2=6 clasificados, 2 BYEs)
                // seeds: 1=1ºA, 2=1ºB, 3=1ºC, 4=2ºA, 5=2ºB, 6=2ºC
                // QF: 1ºA(BYE) | 2ºBvs2ºC[58] | 1ºCvs2ºA[59] | 1ºB(BYE)
                // SF: 1ºAvs G(58)[61] | G(59)vs1ºB[62]
                return new ArrayList<>(Arrays.asList(1, b1, 5, 6, 3, 4, b2, 2));
            } else if (numClassified == 7) {
                // FAP — 10 inscriptos (1Z×4 + 2Z×3 → 3+2+2=7 clasificados, 1 BYE)
                // seeds: 1=1ºA, 2=1ºB, 3=1ºC, 4=2ºA, 5=2ºB, 6=2ºC, 7=3ºA
                // QF: 1ºA(BYE) | 2ºBvs2ºC[58] | 1ºCvs2ºA[59] | 3ºAvs1ºB[60]
                // SF: 1ºAvs G(58)[61] | G(59)vsG(60)[62]
                return new ArrayList<>(Arrays.asList(1, b1, 5, 6, 3, 4, 7, 2));
            } else if (numClassified == 8 && totalPairs == 11) {
                // FAP — 11 inscriptos (2Z×4 + 1Z×3 → 3+3+2=8 clasificados, 0 BYEs)
                // seeds: 1=1ºA, 2=1ºB, 3=1ºC, 4=2ºA, 5=2ºB, 6=2ºC, 7=3ºA, 8=3ºB
                // QF: 1ºAvs3ºB[57] | 2ºBvs2ºC[58] | 1ºCvs2ºA[59] | 3ºAvs1ºB[60]
                // SF: G(57)vsG(58)[61] | G(59)vsG(60)[62]
                return new ArrayList<>(Arrays.asList(1, 8, 5, 6, 3, 4, 7, 2));
            } else {
                // FAP — 12 inscriptos (4Z×3 → 2+2+2+2=8 clasificados, 0 BYEs)
                // seeds: 1=1ºA, 2=1ºB, 3=1ºC, 4=1ºD, 5=2ºA, 6=2ºB, 7=2ºC, 8=2ºD
                // QF: 1ºAvs2ºB[57] | 2ºCvs1ºD[58] | 1ºCvs2ºD[59] | 2ºAvs1ºB[60]
                // SF: G(57)vsG(58)[61] | G(59)vsG(60)[62]
                // Diseño: cada 1° cruza con 2° de otra zona; zone-mates nunca se ven antes de la final
                return new ArrayList<>(Arrays.asList(1, 6, 7, 4, 3, 8, 5, 2));
            }
        }

        if (bracketSize == 16) {
            if (numClassified == 9 && totalPairs == 13) {
                // FAP — 13 inscriptos (1Z×4+3Z×3 → 3+2+2+2=9 clasificados, 7 BYEs)
                // seeds: 1=1ºA, 2=1ºB, 3=1ºC, 4=1ºD, 5=2ºA, 6=2ºB, 7=2ºC, 8=2ºD, 9=3ºA
                // R16: 1ºA(BYE) | 3ºAvs2ºB[50] | 2ºC(BYE) | (BYE)1ºD | 1ºC(BYE) | (BYE)2ºD | 2ºA(BYE) | (BYE)1ºB
                // QF: 1ºAvs G(50)[57] | 2ºCvs1ºD[58] | 1ºCvs2ºD[59] | 2ºAvs1ºB[60]
                // SF: G(57)vsG(58)[61] | G(59)vsG(60)[62]
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE → 1ºA a QF
                    9,   6,   // 3ºA vs 2ºB → prelim [50], ganador a QF vs 1ºA
                    7,  b2,   // 2ºC vs BYE → 2ºC a QF
                    b3,  4,   // BYE vs 1ºD → 1ºD a QF [58 = 2ºC vs 1ºD]
                    3,  b4,   // 1ºC vs BYE → 1ºC a QF
                    b5,  8,   // BYE vs 2ºD → 2ºD a QF [59 = 1ºC vs 2ºD]
                    5,  b6,   // 2ºA vs BYE → 2ºA a QF
                    b7,  2    // BYE vs 1ºB → 1ºB a QF [60 = 2ºA vs 1ºB]
                ));
            } else if (numClassified == 9) {
                // 12 inscriptos con 3Z×4 → 3+3+3=9 clasificados, 7 BYEs
                // (template oficial pendiente — placeholder con algoritmo estándar)
                return generateStandardSeedOrder(bracketSize);
            } else if (numClassified == 10 && totalPairs == 14) {
                // FAP — 14 inscriptos (2Z×4+2Z×3 → 3+3+2+2=10 clasificados, 6 BYEs)
                // seeds: 1=1ºA,2=1ºB,3=1ºC,4=1ºD, 5=2ºA,6=2ºB,7=2ºC,8=2ºD, 9=3ºA,10=3ºB
                // R16: 1ºA(BYE)|3ºAvs2ºB[50]|2ºC(BYE)|(BYE)1ºD|1ºC(BYE)|(BYE)2ºD|2ºAvs3ºB[55]|(BYE)1ºB
                // QF: 1ºAvs G(50)[57] | 2ºCvs1ºD[58] | 1ºCvs2ºD[59] | G(55)vs1ºB[60]
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → 1ºA a QF
                    9,   6,   // 3ºA vs 2ºB  → prelim [50]
                    7,  b2,   // 2ºC vs BYE  → 2ºC a QF
                    b3,  4,   // BYE vs 1ºD  → 1ºD a QF  [QF58 = 2ºC vs 1ºD]
                    3,  b4,   // 1ºC vs BYE  → 1ºC a QF
                    b5,  8,   // BYE vs 2ºD  → 2ºD a QF  [QF59 = 1ºC vs 2ºD]
                    5,  10,   // 2ºA vs 3ºB  → prelim [55]
                    b6,  2    // BYE vs 1ºB  → 1ºB a QF  [QF60 = G(55) vs 1ºB]
                ));
            } else if (numClassified == 10 && totalPairs == 15) {
                // FAP — 15 inscriptos (5Z×3 → 2+2+2+2+2=10 clasificados, 6 BYEs)
                // seeds: 1=1ºA,2=1ºB,3=1ºC,4=1ºD,5=1ºE, 6=2ºA,7=2ºB,8=2ºC,9=2ºD,10=2ºE
                // R16: 1ºA(BYE)|2ºBvs2ºC[50]|1ºE(BYE)|(BYE)1ºD|1ºC(BYE)|(BYE)2ºE|2ºDvs2ºA[55]|(BYE)1ºB
                // QF: 1ºAvs G(50)[57] | 1ºEvs1ºD[58] | 1ºCvs2ºE[59] | G(55)vs1ºB[60]
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → 1ºA a QF
                    7,   8,   // 2ºB vs 2ºC  → prelim [50]
                    5,  b2,   // 1ºE vs BYE  → 1ºE a QF
                    b3,  4,   // BYE vs 1ºD  → 1ºD a QF  [QF58 = 1ºE vs 1ºD]
                    3,  b4,   // 1ºC vs BYE  → 1ºC a QF
                    b5, 10,   // BYE vs 2ºE  → 2ºE a QF  [QF59 = 1ºC vs 2ºE]
                    9,   6,   // 2ºD vs 2ºA  → prelim [55]
                    b6,  2    // BYE vs 1ºB  → 1ºB a QF  [QF60 = G(55) vs 1ºB]
                ));
            } else if (numClassified == 11) {
                // FAP — 16 inscriptos (1Z×4+4Z×3 → 3+2+2+2+2=11 clasificados, 5 BYEs)
                // seeds: 1=1ºA,2=1ºB,3=1ºC,4=1ºD,5=1ºE, 6=2ºA,7=2ºB,8=2ºC,9=2ºD,10=2ºE, 11=3ºA
                // R16: 1ºA(BYE)|2ºBvs2ºC[50]|1ºE(BYE)|(BYE)1ºD|1ºC(BYE)|3ºAvs2ºE[54]|2ºDvs2ºA[55]|(BYE)1ºB
                // QF: 1ºAvs G(50)[57] | 1ºEvs1ºD[58] | 1ºCvs G(54)[59] | G(55)vs1ºB[60]
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → 1ºA a QF
                    7,   8,   // 2ºB vs 2ºC  → prelim [50]
                    5,  b2,   // 1ºE vs BYE  → 1ºE a QF
                    b3,  4,   // BYE vs 1ºD  → 1ºD a QF  [QF58 = 1ºE vs 1ºD]
                    3,  b4,   // 1ºC vs BYE  → 1ºC a QF
                    11, 10,   // 3ºA vs 2ºE  → prelim [54]  [QF59 = 1ºC vs G(54)]
                    9,   6,   // 2ºD vs 2ºA  → prelim [55]
                    b5,  2    // BYE vs 1ºB  → 1ºB a QF  [QF60 = G(55) vs 1ºB]
                ));
            } else if (numClassified == 12 && totalPairs == 17) {
                // FAP — 17 inscriptos (2Z×4+3Z×3 → 3+3+2+2+2=12 clasificados, 4 BYEs)
                // seeds: 1=1ºA,2=1ºB,3=1ºC,4=1ºD,5=1ºE, 6=2ºA,7=2ºB,8=2ºC,9=2ºD,10=2ºE, 11=3ºA,12=3ºB
                // R16: 1ºA(BYE)|2ºBvs2ºC[50]|1ºEvs3ºB[51]|(BYE)1ºD|1ºC(BYE)|3ºAvs2ºE[54]|2ºDvs2ºA[55]|(BYE)1ºB
                // QF: 1ºAvs G(50)[57] | G(51)vs1ºD[58] | 1ºCvs G(54)[59] | G(55)vs1ºB[60]
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → 1ºA a QF
                    7,   8,   // 2ºB vs 2ºC  → prelim [50]
                    5,  12,   // 1ºE vs 3ºB  → prelim [51]
                    b2,  4,   // BYE vs 1ºD  → 1ºD a QF  [QF58 = G(51) vs 1ºD]
                    3,  b3,   // 1ºC vs BYE  → 1ºC a QF
                    11, 10,   // 3ºA vs 2ºE  → prelim [54]  [QF59 = 1ºC vs G(54)]
                    9,   6,   // 2ºD vs 2ºA  → prelim [55]
                    b4,  2    // BYE vs 1ºB  → 1ºB a QF  [QF60 = G(55) vs 1ºB]
                ));
            } else if (numClassified == 12 && totalPairs == 18) {
                // FAP — 18 inscriptos (6Z×3 → 2+2+2+2+2+2=12 clasificados, 4 BYEs)
                // seeds: 1=1ºA,2=1ºB,3=1ºC,4=1ºD,5=1ºE,6=1ºF, 7=2ºA,8=2ºB,9=2ºC,10=2ºD,11=2ºE,12=2ºF
                // R16: 1ºA(BYE)|2ºCvs2ºF[50]|1ºEvs2ºB[51]|(BYE)1ºD|1ºC(BYE)|2ºAvs1ºF[54]|2ºEvs2ºD[55]|(BYE)1ºB
                // QF: 1ºAvs G(50)[57] | G(51)vs1ºD[58] | 1ºCvs G(54)[59] | G(55)vs1ºB[60]
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → 1ºA a QF
                    9,  12,   // 2ºC vs 2ºF  → prelim [50]
                    5,   8,   // 1ºE vs 2ºB  → prelim [51]
                    b2,  4,   // BYE vs 1ºD  → 1ºD a QF  [QF58 = G(51) vs 1ºD]
                    3,  b3,   // 1ºC vs BYE  → 1ºC a QF
                    7,   6,   // 2ºA vs 1ºF  → prelim [54]  [QF59 = 1ºC vs G(54)]
                    11, 10,   // 2ºE vs 2ºD  → prelim [55]
                    b4,  2    // BYE vs 1ºB  → 1ºB a QF  [QF60 = G(55) vs 1ºB]
                ));
            } else if (numClassified == 12) {
                // Otra config. con 12 clasificados y bracketSize 16 — template oficial pendiente
                return generateStandardSeedOrder(bracketSize);
            } else if (numClassified == 13 && totalPairs == 19) {
                // FAP — 19 inscriptos (1Z×4+5Z×3 → 3+2×5=13 clasificados, 3 BYEs)
                // seeds: 1=1ºA,2=1ºB,3=1ºC,4=1ºD,5=1ºE,6=1ºF, 7=2ºA,8=2ºB,9=2ºC,10=2ºD,11=2ºE,12=2ºF, 13=3ºA
                // R16: 1ºA(BYE)|2ºCvs2ºF[50]|1ºEvs2ºB[51]|3ºAvs1ºD[52]|1ºC(BYE)|2ºAvs1ºF[54]|2ºEvs2ºD[55]|(BYE)1ºB
                // QF: 1ºAvs G(50)[57] | G(51)vsG(52)[58] | 1ºCvs G(54)[59] | G(55)vs1ºB[60]
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → 1ºA a QF
                    9,  12,   // 2ºC vs 2ºF  → prelim [50]
                    5,   8,   // 1ºE vs 2ºB  → prelim [51]
                    13,  4,   // 3ºA vs 1ºD  → prelim [52]  [QF58 = G(51) vs G(52)]
                    3,  b2,   // 1ºC vs BYE  → 1ºC a QF
                    7,   6,   // 2ºA vs 1ºF  → prelim [54]  [QF59 = 1ºC vs G(54)]
                    11, 10,   // 2ºE vs 2ºD  → prelim [55]
                    b3,  2    // BYE vs 1ºB  → 1ºB a QF  [QF60 = G(55) vs 1ºB]
                ));
            } else if (numClassified == 14 && totalPairs == 20) {
                // FAP — 20 inscriptos (2Z×4+4Z×3 → 6+6+2=14 clasificados, 2 BYEs)
                // seeds: 1=1ºA,2=1ºB,3=1ºC,4=1ºD,5=1ºE,6=1ºF, 7=2ºA,8=2ºB,9=2ºC,10=2ºD,11=2ºE,12=2ºF, 13=3ºA,14=3ºB
                // R16: 1ºA(BYE)|2ºCvs2ºF[50]|1ºEvs2ºB[51]|3ºAvs1ºD[52]|1ºCvs3ºB[53]|2ºAvs1ºF[54]|2ºEvs2ºD[55]|(BYE)1ºB
                // QF: 1ºAvs G(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vs1ºB[60]
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → 1ºA a QF
                    9,  12,   // 2ºC vs 2ºF  → prelim [50]
                    5,   8,   // 1ºE vs 2ºB  → prelim [51]
                    13,  4,   // 3ºA vs 1ºD  → prelim [52]  [QF58 = G(51) vs G(52)]
                    3,  14,   // 1ºC vs 3ºB  → prelim [53]
                    7,   6,   // 2ºA vs 1ºF  → prelim [54]  [QF59 = G(53) vs G(54)]
                    11, 10,   // 2ºE vs 2ºD  → prelim [55]
                    b2,  2    // BYE vs 1ºB  → 1ºB a QF  [QF60 = G(55) vs 1ºB]
                ));
            } else if (numClassified == 14 && totalPairs == 21) {
                // FAP — 21 inscriptos (7Z×3 → 7+7=14 clasificados, 2 BYEs)
                // seeds: 1=1ºA,2=1ºB,3=1ºC,4=1ºD,5=1ºE,6=1ºF,7=1ºG, 8=2ºA,9=2ºB,10=2ºC,11=2ºD,12=2ºE,13=2ºF,14=2ºG
                // R16: 1ºA(BYE)|2ºFvs2ºG[50]|1ºEvs2ºC[51]|2ºBvs1ºD[52]|1ºCvs2ºA[53]|2ºDvs1ºF[54]|1ºGvs2ºE[55]|(BYE)1ºB
                // QF: 1ºAvs G(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vs1ºB[60]
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → 1ºA a QF
                    13, 14,   // 2ºF vs 2ºG  → prelim [50]
                    5,  10,   // 1ºE vs 2ºC  → prelim [51]
                    9,   4,   // 2ºB vs 1ºD  → prelim [52]  [QF58 = G(51) vs G(52)]
                    3,   8,   // 1ºC vs 2ºA  → prelim [53]
                    11,  6,   // 2ºD vs 1ºF  → prelim [54]  [QF59 = G(53) vs G(54)]
                    7,  12,   // 1ºG vs 2ºE  → prelim [55]
                    b2,  2    // BYE vs 1ºB  → 1ºB a QF  [QF60 = G(55) vs 1ºB]
                ));
            } else if (numClassified >= 13 && numClassified <= 14) {
                // 13–14 cl. — configuraciones distintas a las oficiales anteriores (template pendiente)
                return generateStandardSeedOrder(bracketSize);
            } else if (numClassified == 15 && totalPairs == 22) {
                // FAP — 22 inscriptos (1Z×4+6Z×3 → 7+7+1=15 clasificados, 1 BYE)
                // seeds: 1=1ºA,2=1ºB,3=1ºC,4=1ºD,5=1ºE,6=1ºF,7=1ºG, 8=2ºA,9=2ºB,10=2ºC,11=2ºD,12=2ºE,13=2ºF,14=2ºG, 15=3ºA
                // R16: 1ºA(BYE)|2ºFvs2ºG[50]|1ºEvs2ºC[51]|2ºBvs1ºD[52]|1ºCvs2ºA[53]|2ºDvs1ºF[54]|1ºGvs2ºE[55]|3ºAvs1ºB[56]
                // QF: 1ºAvs G(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vsG(56)[60]
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → 1ºA a QF
                    13, 14,   // 2ºF vs 2ºG  → prelim [50]
                    5,  10,   // 1ºE vs 2ºC  → prelim [51]
                    9,   4,   // 2ºB vs 1ºD  → prelim [52]  [QF58 = G(51) vs G(52)]
                    3,   8,   // 1ºC vs 2ºA  → prelim [53]
                    11,  6,   // 2ºD vs 1ºF  → prelim [54]  [QF59 = G(53) vs G(54)]
                    7,  12,   // 1ºG vs 2ºE  → prelim [55]
                    15,  2    // 3ºA vs 1ºB  → prelim [56]  [QF60 = G(55) vs G(56)]
                ));
            } else if (numClassified == 16 && totalPairs == 24) {
                // FAP — 24 inscriptos (8Z×3 → 8+8=16 clasificados, 0 BYEs — bracket lleno)
                // seeds: 1=1ºA,2=1ºB,3=1ºC,4=1ºD,5=1ºE,6=1ºF,7=1ºG,8=1ºH, 9=2ºA,10=2ºB,11=2ºC,12=2ºD,13=2ºE,14=2ºF,15=2ºG,16=2ºH
                // R16(49-56): 1ºAvs2ºB[49]|2ºGvs1ºH[50]|1ºEvs2ºF[51]|2ºCvs1ºD[52]|1ºCvs2ºD[53]|2ºEvs1ºF[54]|1ºGvs2ºH[55]|2ºAvs1ºB[56]
                // QF: G(49)vsG(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vsG(56)[60]
                return new ArrayList<>(Arrays.asList(
                    1,  10,   // 1ºA vs 2ºB  → prelim [49]
                    15,  8,   // 2ºG vs 1ºH  → prelim [50]  [QF57 = G(49) vs G(50)]
                    5,  14,   // 1ºE vs 2ºF  → prelim [51]
                    11,  4,   // 2ºC vs 1ºD  → prelim [52]  [QF58 = G(51) vs G(52)]
                    3,  12,   // 1ºC vs 2ºD  → prelim [53]
                    13,  6,   // 2ºE vs 1ºF  → prelim [54]  [QF59 = G(53) vs G(54)]
                    7,  16,   // 1ºG vs 2ºH  → prelim [55]
                    9,   2    // 2ºA vs 1ºB  → prelim [56]  [QF60 = G(55) vs G(56)]
                ));
            } else if (numClassified == 16 && totalPairs == 23) {
                // FAP — 23 inscriptos (2Z×4+5Z×3 → 7+7+2=16 clasificados, 0 BYEs — bracket lleno)
                // seeds: 1=1ºA,2=1ºB,3=1ºC,4=1ºD,5=1ºE,6=1ºF,7=1ºG, 8=2ºA,9=2ºB,10=2ºC,11=2ºD,12=2ºE,13=2ºF,14=2ºG, 15=3ºA,16=3ºB
                // R16(49-56): 1ºAvs3ºB[49]|2ºFvs2ºG[50]|1ºEvs2ºC[51]|2ºBvs1ºD[52]|1ºCvs2ºA[53]|2ºDvs1ºF[54]|1ºGvs2ºE[55]|3ºAvs1ºB[56]
                // QF: G(49)vsG(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vsG(56)[60]
                return new ArrayList<>(Arrays.asList(
                    1,  16,   // 1ºA vs 3ºB  → prelim [49]
                    13, 14,   // 2ºF vs 2ºG  → prelim [50]  [QF57 = G(49) vs G(50)]
                    5,  10,   // 1ºE vs 2ºC  → prelim [51]
                    9,   4,   // 2ºB vs 1ºD  → prelim [52]  [QF58 = G(51) vs G(52)]
                    3,   8,   // 1ºC vs 2ºA  → prelim [53]
                    11,  6,   // 2ºD vs 1ºF  → prelim [54]  [QF59 = G(53) vs G(54)]
                    7,  12,   // 1ºG vs 2ºE  → prelim [55]
                    15,  2    // 3ºA vs 1ºB  → prelim [56]  [QF60 = G(55) vs G(56)]
                ));
            } else {
                // Cualquier otra cantidad → algoritmo estándar
                return generateStandardSeedOrder(bracketSize);
            }
        }

        if (bracketSize == 32) {
            if (numClassified == 17 && totalPairs == 25) {
                // FAP — 25 inscriptos (1Z×4+7Z×3 → 8+8+1=17 clasificados, 15 BYEs)
                // seeds: 1-8=1°(A-H), 9-16=2°(A-H), 17=3ºA
                // R32: 1 partido real (34): 3ºAvs2ºB → ganador a R16/49
                // R16: 49=1ºAvs G(34) | 50=2ºGvs1ºH | 51=1ºEvs2ºF | 52=2ºCvs1ºD | 53=1ºCvs2ºD | 54=2ºEvs1ºF | 55=1ºGvs2ºH | 56=2ºAvs1ºB
                // QF: G(49)vsG(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vsG(56)[60]
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → R16/49 lado 1
                    17, 10,   // 3ºA vs 2ºB  → prelim [34]  → R16/49 lado 2
                    15, b2,   // 2ºG vs BYE  → R16/50 lado 1
                    b3,  8,   // BYE vs 1ºH  → R16/50 lado 2  [R16/50 = 2ºG vs 1ºH]
                    5,  b4,   // 1ºE vs BYE  → R16/51 lado 1
                    14, b5,   // 2ºF vs BYE  → R16/51 lado 2  [R16/51 = 1ºE vs 2ºF]
                    11, b6,   // 2ºC vs BYE  → R16/52 lado 1
                    b7,  4,   // BYE vs 1ºD  → R16/52 lado 2  [R16/52 = 2ºC vs 1ºD]
                    3,  b8,   // 1ºC vs BYE  → R16/53 lado 1
                    12, b9,   // 2ºD vs BYE  → R16/53 lado 2  [R16/53 = 1ºC vs 2ºD]
                    13, b10,  // 2ºE vs BYE  → R16/54 lado 1
                    b11, 6,   // BYE vs 1ºF  → R16/54 lado 2  [R16/54 = 2ºE vs 1ºF]
                    7,  b12,  // 1ºG vs BYE  → R16/55 lado 1
                    b13, 16,  // BYE vs 2ºH  → R16/55 lado 2  [R16/55 = 1ºG vs 2ºH]
                    9,  b14,  // 2ºA vs BYE  → R16/56 lado 1
                    b15, 2    // BYE vs 1ºB  → R16/56 lado 2  [R16/56 = 2ºA vs 1ºB]
                ));
            } else if (numClassified == 20 && totalPairs == 29) {
                // FAP — 29 inscriptos (2Z×4+7Z×3 → 9+9+2=20 clasificados, 12 BYEs)
                // seeds: 1-9=1°(A-I), 10-18=2°(A-I), 19=3ºA, 20=3ºB
                // R32: 4 partidos reales: [34]=2ºBvs2ºC→R16/49 | [39]=2ºFvs3ºB→R16/52 | [42]=3ºAvs2ºE→R16/53 | [47]=2ºDvs2ºA→R16/56
                // R16: 49=1ºAvs G(34) | 50=1ºIvs1ºH | 51=1ºEvs2ºG | 52=G(39)vs1ºD | 53=1ºCvs G(42) | 54=2ºHvs1ºF | 55=1ºGvs2ºI | 56=G(47)vs1ºB
                // QF: G(49)vsG(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vsG(56)[60]
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → R16/49 lado 1
                    11, 12,   // 2ºB vs 2ºC  → prelim [34]  → R16/49 lado 2
                    9,  b2,   // 1ºI vs BYE  → R16/50 lado 1
                    b3,  8,   // BYE vs 1ºH  → R16/50 lado 2  [R16/50 = 1ºI vs 1ºH]
                    5,  b4,   // 1ºE vs BYE  → R16/51 lado 1
                    16, b5,   // 2ºG vs BYE  → R16/51 lado 2  [R16/51 = 1ºE vs 2ºG]
                    15, 20,   // 2ºF vs 3ºB  → prelim [39]  → R16/52 lado 1
                    b6,  4,   // BYE vs 1ºD  → R16/52 lado 2  [R16/52 = G(39) vs 1ºD]
                    3,  b7,   // 1ºC vs BYE  → R16/53 lado 1
                    19, 14,   // 3ºA vs 2ºE  → prelim [42]  → R16/53 lado 2  [R16/53 = 1ºC vs G(42)]
                    17, b8,   // 2ºH vs BYE  → R16/54 lado 1
                    b9,  6,   // BYE vs 1ºF  → R16/54 lado 2  [R16/54 = 2ºH vs 1ºF]
                    7,  b10,  // 1ºG vs BYE  → R16/55 lado 1
                    18, b11,  // 2ºI vs BYE  → R16/55 lado 2  [R16/55 = 1ºG vs 2ºI]
                    13, 10,   // 2ºD vs 2ºA  → prelim [47]  → R16/56 lado 1
                    b12, 2    // BYE vs 1ºB  → R16/56 lado 2  [R16/56 = G(47) vs 1ºB]
                ));
            } else if (numClassified == 19 && totalPairs == 28) {
                // FAP — 28 inscriptos (1Z×4+8Z×3 → 9+9+1=19 clasificados, 13 BYEs)
                // seeds: 1-9=1°(A-I), 10-18=2°(A-I), 19=3ºA
                // R32: 3 partidos reales: [34]=2ºBvs2ºC→R16/49 | [42]=3ºAvs2ºE→R16/53 | [47]=2ºDvs2ºA→R16/56
                // R16: 49=1ºAvs G(34) | 50=1ºHvs1ºI | 51=1ºEvs2ºG | 52=2ºFvs1ºD | 53=1ºCvs G(42) | 54=2ºHvs1ºF | 55=1ºGvs2ºI | 56=G(47)vs1ºB
                // QF: G(49)vsG(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vsG(56)[60]
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → R16/49 lado 1
                    11, 12,   // 2ºB vs 2ºC  → prelim [34]  → R16/49 lado 2
                    8,  b2,   // 1ºH vs BYE  → R16/50 lado 1
                    b3,  9,   // BYE vs 1ºI  → R16/50 lado 2  [R16/50 = 1ºH vs 1ºI]
                    5,  b4,   // 1ºE vs BYE  → R16/51 lado 1
                    16, b5,   // 2ºG vs BYE  → R16/51 lado 2  [R16/51 = 1ºE vs 2ºG]
                    15, b6,   // 2ºF vs BYE  → R16/52 lado 1
                    b7,  4,   // BYE vs 1ºD  → R16/52 lado 2  [R16/52 = 2ºF vs 1ºD]
                    3,  b8,   // 1ºC vs BYE  → R16/53 lado 1
                    19, 14,   // 3ºA vs 2ºE  → prelim [42]  → R16/53 lado 2  [R16/53 = 1ºC vs G(42)]
                    17, b9,   // 2ºH vs BYE  → R16/54 lado 1
                    b10, 6,   // BYE vs 1ºF  → R16/54 lado 2  [R16/54 = 2ºH vs 1ºF]
                    7,  b11,  // 1ºG vs BYE  → R16/55 lado 1
                    18, b12,  // 2ºI vs BYE  → R16/55 lado 2  [R16/55 = 1ºG vs 2ºI]
                    13, 10,   // 2ºD vs 2ºA  → prelim [47]  → R16/56 lado 1
                    b13, 2    // BYE vs 1ºB  → R16/56 lado 2  [R16/56 = G(47) vs 1ºB]
                ));
            } else if (numClassified == 18 && totalPairs == 27) {
                // FAP — 27 inscriptos (9Z×3 → 9+9=18 clasificados, 14 BYEs)
                // seeds: 1-9=1°(A-I), 10-18=2°(A-I)
                // R32: 2 partidos reales: [34]=2ºBvs2ºC → R16/49 con 1ºA | [47]=2ºDvs2ºA → R16/56 con 1ºB
                // R16: 49=1ºAvs G(34) | 50=1ºHvs1ºI | 51=1ºEvs2ºG | 52=2ºFvs1ºD | 53=1ºCvs2ºE | 54=2ºHvs1ºF | 55=1ºGvs2ºI | 56=G(47)vs1ºB
                // QF: G(49)vsG(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vsG(56)[60]
                // Nota: R16/50=1ºHvs1ºI — únicos dos 1°s que se enfrentan en R16 (9 zonas, impar)
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → R16/49 lado 1
                    11, 12,   // 2ºB vs 2ºC  → prelim [34]  → R16/49 lado 2
                    8,  b2,   // 1ºH vs BYE  → R16/50 lado 1
                    b3,  9,   // BYE vs 1ºI  → R16/50 lado 2  [R16/50 = 1ºH vs 1ºI]
                    5,  b4,   // 1ºE vs BYE  → R16/51 lado 1
                    16, b5,   // 2ºG vs BYE  → R16/51 lado 2  [R16/51 = 1ºE vs 2ºG]
                    15, b6,   // 2ºF vs BYE  → R16/52 lado 1
                    b7,  4,   // BYE vs 1ºD  → R16/52 lado 2  [R16/52 = 2ºF vs 1ºD]
                    3,  b8,   // 1ºC vs BYE  → R16/53 lado 1
                    14, b9,   // 2ºE vs BYE  → R16/53 lado 2  [R16/53 = 1ºC vs 2ºE]
                    17, b10,  // 2ºH vs BYE  → R16/54 lado 1
                    b11, 6,   // BYE vs 1ºF  → R16/54 lado 2  [R16/54 = 2ºH vs 1ºF]
                    7,  b12,  // 1ºG vs BYE  → R16/55 lado 1
                    18, b13,  // 2ºI vs BYE  → R16/55 lado 2  [R16/55 = 1ºG vs 2ºI]
                    13, 10,   // 2ºD vs 2ºA  → prelim [47]  → R16/56 lado 1
                    b14, 2    // BYE vs 1ºB  → R16/56 lado 2  [R16/56 = G(47) vs 1ºB]
                ));
            } else if (numClassified == 18 && totalPairs == 26) {
                // FAP — 26 inscriptos (2Z×4+6Z×3 → 8+8+2=18 clasificados, 14 BYEs)
                // seeds: 1-8=1°(A-H), 9-16=2°(A-H), 17=3ºA, 18=3ºB
                // R32: 2 partidos reales: [34]=3ºAvs2ºB → R16 con 1ºA | [35]=2ºAvs3ºB → R16 con 1ºB
                // R16: 49=1ºAvs G(34) | 50=2ºGvs1ºH | 51=1ºEvs2ºF | 52=2ºCvs1ºD | 53=1ºCvs2ºD | 54=2ºEvs1ºF | 55=1ºGvs2ºH | 56=G(35)vs1ºB
                // QF: G(49)vsG(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vsG(56)[60]
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → R16/49 lado 1
                    17, 10,   // 3ºA vs 2ºB  → prelim [34]  → R16/49 lado 2
                    15, b2,   // 2ºG vs BYE  → R16/50 lado 1
                    b3,  8,   // BYE vs 1ºH  → R16/50 lado 2  [R16/50 = 2ºG vs 1ºH]
                    5,  b4,   // 1ºE vs BYE  → R16/51 lado 1
                    14, b5,   // 2ºF vs BYE  → R16/51 lado 2  [R16/51 = 1ºE vs 2ºF]
                    11, b6,   // 2ºC vs BYE  → R16/52 lado 1
                    b7,  4,   // BYE vs 1ºD  → R16/52 lado 2  [R16/52 = 2ºC vs 1ºD]
                    3,  b8,   // 1ºC vs BYE  → R16/53 lado 1
                    12, b9,   // 2ºD vs BYE  → R16/53 lado 2  [R16/53 = 1ºC vs 2ºD]
                    13, b10,  // 2ºE vs BYE  → R16/54 lado 1
                    b11, 6,   // BYE vs 1ºF  → R16/54 lado 2  [R16/54 = 2ºE vs 1ºF]
                    7,  b12,  // 1ºG vs BYE  → R16/55 lado 1
                    b13, 16,  // BYE vs 2ºH  → R16/55 lado 2  [R16/55 = 1ºG vs 2ºH]
                    9,  18,   // 2ºA vs 3ºB  → prelim [35]  → R16/56 lado 1
                    b14, 2    // BYE vs 1ºB  → R16/56 lado 2  [R16/56 = G(35) vs 1ºB]
                ));
            } else if (numClassified == 22 && totalPairs == 32) {
                // FAP — 32 inscriptos (2Z×4+8Z×3 → 10+10+2=22 clasificados, 10 BYEs)
                // seeds: 1-10=1°(A-J), 11-20=2°(A-J), 21=3ºA, 22=3ºB
                // R32: 6 partidos reales: [34]=2ºCvs2ºF→R16/49 | [38]=3ºBvs2ºJ→R16/51 | [39]=2ºGvs2ºB→R16/52 | [42]=2ºAvs2ºH→R16/53 | [43]=2ºIvs3ºA→R16/54 | [47]=2ºEvs2ºD→R16/56
                // R16: 49=1ºAvs G(34) | 50=1ºIvs1ºH | 51=1ºEvs G(38) | 52=G(39)vs1ºD | 53=1ºCvs G(42) | 54=G(43)vs1ºF | 55=1ºGvs1ºJ | 56=G(47)vs1ºB
                // QF: G(49)vsG(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vsG(56)[60]
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → R16/49 lado 1
                    13, 16,   // 2ºC vs 2ºF  → prelim [34]  → R16/49 lado 2
                    b2,  9,   // BYE vs 1ºI  → R16/50 lado 1
                    b3,  8,   // BYE vs 1ºH  → R16/50 lado 2  [R16/50 = 1ºI vs 1ºH]
                    b4,  5,   // BYE vs 1ºE  → R16/51 lado 1
                    22, 20,   // 3ºB vs 2ºJ  → prelim [38]  → R16/51 lado 2  [R16/51 = 1ºE vs G(38)]
                    17, 12,   // 2ºG vs 2ºB  → prelim [39]  → R16/52 lado 1
                    b5,  4,   // BYE vs 1ºD  → R16/52 lado 2  [R16/52 = G(39) vs 1ºD]
                    3,  b6,   // 1ºC vs BYE  → R16/53 lado 1
                    11, 18,   // 2ºA vs 2ºH  → prelim [42]  → R16/53 lado 2  [R16/53 = 1ºC vs G(42)]
                    19, 21,   // 2ºI vs 3ºA  → prelim [43]  → R16/54 lado 1
                    b7,  6,   // BYE vs 1ºF  → R16/54 lado 2  [R16/54 = G(43) vs 1ºF]
                    b8,  7,   // BYE vs 1ºG  → R16/55 lado 1
                    b9, 10,   // BYE vs 1ºJ  → R16/55 lado 2  [R16/55 = 1ºG vs 1ºJ]
                    15, 14,   // 2ºE vs 2ºD  → prelim [47]  → R16/56 lado 1
                    b10, 2    // BYE vs 1ºB  → R16/56 lado 2  [R16/56 = G(47) vs 1ºB]
                ));
            } else if (numClassified == 21 && totalPairs == 31) {
                // FAP — 31 inscriptos (1Z×4+9Z×3 → 10+10+1=21 clasificados, 11 BYEs)
                // seeds: 1-10=1°(A-J), 11-20=2°(A-J), 21=3ºA
                // R32: 5 partidos reales: [34]=2ºCvs2ºF→R16/49 | [39]=2ºGvs2ºB→R16/52 | [42]=2ºAvs2ºH→R16/53 | [43]=2ºIvs3ºA→R16/54 | [47]=2ºEvs2ºD→R16/56
                // R16: 49=1ºAvs G(34) | 50=1ºIvs1ºH | 51=1ºEvs2ºJ | 52=G(39)vs1ºD | 53=1ºCvs G(42) | 54=G(43)vs1ºF | 55=1ºGvs1ºJ | 56=G(47)vs1ºB
                // QF: G(49)vsG(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vsG(56)[60]
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → R16/49 lado 1
                    13, 16,   // 2ºC vs 2ºF  → prelim [34]  → R16/49 lado 2
                    b2,  9,   // BYE vs 1ºI  → R16/50 lado 1
                    b3,  8,   // BYE vs 1ºH  → R16/50 lado 2  [R16/50 = 1ºI vs 1ºH]
                    b4,  5,   // BYE vs 1ºE  → R16/51 lado 1
                    b5, 20,   // BYE vs 2ºJ  → R16/51 lado 2  [R16/51 = 1ºE vs 2ºJ]
                    17, 12,   // 2ºG vs 2ºB  → prelim [39]  → R16/52 lado 1
                    b6,  4,   // BYE vs 1ºD  → R16/52 lado 2  [R16/52 = G(39) vs 1ºD]
                    3,  b7,   // 1ºC vs BYE  → R16/53 lado 1
                    11, 18,   // 2ºA vs 2ºH  → prelim [42]  → R16/53 lado 2  [R16/53 = 1ºC vs G(42)]
                    19, 21,   // 2ºI vs 3ºA  → prelim [43]  → R16/54 lado 1
                    b8,  6,   // BYE vs 1ºF  → R16/54 lado 2  [R16/54 = G(43) vs 1ºF]
                    b9,  7,   // BYE vs 1ºG  → R16/55 lado 1
                    b10, 10,  // BYE vs 1ºJ  → R16/55 lado 2  [R16/55 = 1ºG vs 1ºJ]
                    15, 14,   // 2ºE vs 2ºD  → prelim [47]  → R16/56 lado 1
                    b11, 2    // BYE vs 1ºB  → R16/56 lado 2  [R16/56 = G(47) vs 1ºB]
                ));
            } else if (numClassified == 20 && totalPairs == 30) {
                // FAP — 30 inscriptos (10Z×3 → 10+10=20 clasificados, 12 BYEs)
                // seeds: 1-10=1°(A-J), 11-20=2°(A-J)
                // R32: 4 partidos reales: [34]=2ºCvs2ºF→R16/49 | [39]=2ºGvs2ºB→R16/52 | [42]=2ºAvs2ºH→R16/53 | [47]=2ºEvs2ºD→R16/56
                // R16: 49=1ºAvs G(34) | 50=1ºIvs1ºH | 51=1ºEvs2ºJ | 52=G(39)vs1ºD | 53=1ºCvs G(42) | 54=2ºIvs1ºF | 55=1ºGvs1ºJ | 56=G(47)vs1ºB
                // QF: G(49)vsG(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vsG(56)[60]
                // Nota: R16/50=1ºIvs1ºH y R16/55=1ºGvs1ºJ — dos cruces 1°vs1° (10 zonas, par con exceso)
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → R16/49 lado 1
                    13, 16,   // 2ºC vs 2ºF  → prelim [34]  → R16/49 lado 2
                    b2,  9,   // BYE vs 1ºI  → R16/50 lado 1
                    b3,  8,   // BYE vs 1ºH  → R16/50 lado 2  [R16/50 = 1ºI vs 1ºH]
                    b4,  5,   // BYE vs 1ºE  → R16/51 lado 1
                    b5, 20,   // BYE vs 2ºJ  → R16/51 lado 2  [R16/51 = 1ºE vs 2ºJ]
                    17, 12,   // 2ºG vs 2ºB  → prelim [39]  → R16/52 lado 1
                    b6,  4,   // BYE vs 1ºD  → R16/52 lado 2  [R16/52 = G(39) vs 1ºD]
                    3,  b7,   // 1ºC vs BYE  → R16/53 lado 1
                    11, 18,   // 2ºA vs 2ºH  → prelim [42]  → R16/53 lado 2  [R16/53 = 1ºC vs G(42)]
                    b8, 19,   // BYE vs 2ºI  → R16/54 lado 1
                    b9,  6,   // BYE vs 1ºF  → R16/54 lado 2  [R16/54 = 2ºI vs 1ºF]
                    b10, 7,   // BYE vs 1ºG  → R16/55 lado 1
                    b11, 10,  // BYE vs 1ºJ  → R16/55 lado 2  [R16/55 = 1ºG vs 1ºJ]
                    15, 14,   // 2ºE vs 2ºD  → prelim [47]  → R16/56 lado 1
                    b12, 2    // BYE vs 1ºB  → R16/56 lado 2  [R16/56 = G(47) vs 1ºB]
                ));
            } else if (numClassified == 22 && totalPairs == 33) {
                // FAP — 33 inscriptos (11Z×3 → 11+11=22 clasificados, 10 BYEs)
                // seeds: 1-11=1°(A-K), 12-22=2°(A-K)
                // R32: 6 partidos reales: [34]=2ºFvs2ºG→R16/49 | [38]=2ºBvs2ºK→R16/51 | [39]=2ºJvs2ºC→R16/52 | [42]=2ºDvs2ºI→R16/53 | [43]=1ºKvs2ºA→R16/54 | [47]=2ºHvs2ºE→R16/56
                // R16: 49=1ºAvs G(34) | 50=1ºIvs1ºH | 51=1ºEvs G(38) | 52=G(39)vs1ºD | 53=1ºCvs G(42) | 54=G(43)vs1ºF | 55=1ºGvs1ºJ | 56=G(47)vs1ºB
                // QF: G(49)vsG(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vsG(56)[60]
                // Nota: R16/50=1ºIvs1ºH y R16/55=1ºGvs1ºJ — dos cruces 1°vs1° (11 zonas)
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → R16/49 lado 1
                    17, 18,   // 2ºF vs 2ºG  → prelim [34]  → R16/49 lado 2
                    b2,  9,   // BYE vs 1ºI  → R16/50 lado 1
                    b3,  8,   // BYE vs 1ºH  → R16/50 lado 2  [R16/50 = 1ºI vs 1ºH]
                    b4,  5,   // BYE vs 1ºE  → R16/51 lado 1
                    13, 22,   // 2ºB vs 2ºK  → prelim [38]  → R16/51 lado 2  [R16/51 = 1ºE vs G(38)]
                    21, 14,   // 2ºJ vs 2ºC  → prelim [39]  → R16/52 lado 1
                    b5,  4,   // BYE vs 1ºD  → R16/52 lado 2  [R16/52 = G(39) vs 1ºD]
                    3,  b6,   // 1ºC vs BYE  → R16/53 lado 1
                    15, 20,   // 2ºD vs 2ºI  → prelim [42]  → R16/53 lado 2  [R16/53 = 1ºC vs G(42)]
                    11, 12,   // 1ºK vs 2ºA  → prelim [43]  → R16/54 lado 1
                    b7,  6,   // BYE vs 1ºF  → R16/54 lado 2  [R16/54 = G(43) vs 1ºF]
                    b8,  7,   // BYE vs 1ºG  → R16/55 lado 1
                    b9, 10,   // BYE vs 1ºJ  → R16/55 lado 2  [R16/55 = 1ºG vs 1ºJ]
                    19, 16,   // 2ºH vs 2ºE  → prelim [47]  → R16/56 lado 1
                    b10, 2    // BYE vs 1ºB  → R16/56 lado 2  [R16/56 = G(47) vs 1ºB]
                ));
            } else if (numClassified == 23 && totalPairs == 34) {
                // FAP — 34 inscriptos (1Z×4+10Z×3 → 11+11+1=23 clasificados, 9 BYEs)
                // seeds: 1-11=1°(A-K), 12-22=2°(A-K), 23=3ºA
                // R32: 7 partidos reales: [34]=2ºFvs2ºG | [38]=2ºBvs2ºK | [39]=2ºJvs2ºC | [42]=2ºDvs2ºI | [43]=1ºKvs2ºA | [46]=3ºAvs1ºJ | [47]=2ºHvs2ºE
                // R16: 49=1ºAvs G(34) | 50=1ºIvs1ºH | 51=1ºEvs G(38) | 52=G(39)vs1ºD | 53=1ºCvs G(42) | 54=G(43)vs1ºF | 55=1ºGvs G(46) | 56=G(47)vs1ºB
                // QF: G(49)vsG(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vsG(56)[60]
                // Nota: R16/50=1ºIvs1ºH — un cruce 1°vs1° (11 zonas); 3ºA debuta en R32
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → R16/49 lado 1
                    17, 18,   // 2ºF vs 2ºG  → prelim [34]  → R16/49 lado 2
                    b2,  9,   // BYE vs 1ºI  → R16/50 lado 1
                    b3,  8,   // BYE vs 1ºH  → R16/50 lado 2  [R16/50 = 1ºI vs 1ºH]
                    b4,  5,   // BYE vs 1ºE  → R16/51 lado 1
                    13, 22,   // 2ºB vs 2ºK  → prelim [38]  → R16/51 lado 2  [R16/51 = 1ºE vs G(38)]
                    21, 14,   // 2ºJ vs 2ºC  → prelim [39]  → R16/52 lado 1
                    b5,  4,   // BYE vs 1ºD  → R16/52 lado 2  [R16/52 = G(39) vs 1ºD]
                    3,  b6,   // 1ºC vs BYE  → R16/53 lado 1
                    15, 20,   // 2ºD vs 2ºI  → prelim [42]  → R16/53 lado 2  [R16/53 = 1ºC vs G(42)]
                    11, 12,   // 1ºK vs 2ºA  → prelim [43]  → R16/54 lado 1
                    b7,  6,   // BYE vs 1ºF  → R16/54 lado 2  [R16/54 = G(43) vs 1ºF]
                    b8,  7,   // BYE vs 1ºG  → R16/55 lado 1
                    23, 10,   // 3ºA vs 1ºJ  → prelim [46]  → R16/55 lado 2  [R16/55 = 1ºG vs G(46)]
                    19, 16,   // 2ºH vs 2ºE  → prelim [47]  → R16/56 lado 1
                    b9,  2    // BYE vs 1ºB  → R16/56 lado 2  [R16/56 = G(47) vs 1ºB]
                ));
            } else if (numClassified == 24 && totalPairs == 35) {
                // FAP — 35 inscriptos (2Z×4+9Z×3 → 11+11+2=24 clasificados, 8 BYEs)
                // seeds: 1-11=1°(A-K), 12-22=2°(A-K), 23=3ºA, 24=3ºB
                // R32: 8 partidos reales: [34]=2ºFvs2ºG | [35]=1ºIvs3ºB | [38]=2ºBvs2ºK | [39]=2ºJvs2ºC | [42]=2ºDvs2ºI | [43]=1ºKvs2ºA | [46]=3ºAvs1ºJ | [47]=2ºHvs2ºE
                // R16: 49=1ºAvs G(34) | 50=G(35)vs1ºH | 51=1ºEvs G(38) | 52=G(39)vs1ºD | 53=1ºCvs G(42) | 54=G(43)vs1ºF | 55=1ºGvs G(46) | 56=G(47)vs1ºB
                // QF: G(49)vsG(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vsG(56)[60]
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → R16/49 lado 1
                    17, 18,   // 2ºF vs 2ºG  → prelim [34]  → R16/49 lado 2
                    9,  24,   // 1ºI vs 3ºB  → prelim [35]  → R16/50 lado 1
                    b2,  8,   // BYE vs 1ºH  → R16/50 lado 2  [R16/50 = G(35) vs 1ºH]
                    b3,  5,   // BYE vs 1ºE  → R16/51 lado 1
                    13, 22,   // 2ºB vs 2ºK  → prelim [38]  → R16/51 lado 2  [R16/51 = 1ºE vs G(38)]
                    21, 14,   // 2ºJ vs 2ºC  → prelim [39]  → R16/52 lado 1
                    b4,  4,   // BYE vs 1ºD  → R16/52 lado 2  [R16/52 = G(39) vs 1ºD]
                    3,  b5,   // 1ºC vs BYE  → R16/53 lado 1
                    15, 20,   // 2ºD vs 2ºI  → prelim [42]  → R16/53 lado 2  [R16/53 = 1ºC vs G(42)]
                    11, 12,   // 1ºK vs 2ºA  → prelim [43]  → R16/54 lado 1
                    b6,  6,   // BYE vs 1ºF  → R16/54 lado 2  [R16/54 = G(43) vs 1ºF]
                    b7,  7,   // BYE vs 1ºG  → R16/55 lado 1
                    23, 10,   // 3ºA vs 1ºJ  → prelim [46]  → R16/55 lado 2  [R16/55 = 1ºG vs G(46)]
                    19, 16,   // 2ºH vs 2ºE  → prelim [47]  → R16/56 lado 1
                    b8,  2    // BYE vs 1ºB  → R16/56 lado 2  [R16/56 = G(47) vs 1ºB]
                ));
            } else if (numClassified == 24 && totalPairs == 36) {
                // FAP — 36 inscriptos (12Z×3 → 12+12=24 clasificados, 8 BYEs — sin terceros)
                // seeds: 1-12=1°(A-L), 13-24=2°(A-L)
                // R32: 8 partidos reales: [34]=2ºGvs2ºJ | [35]=1ºIvs2ºB | [38]=2ºCvs1ºL | [39]=2ºKvs2ºF | [42]=2ºEvs2ºL | [43]=1ºKvs2ºD | [46]=2ºAvs1ºJ | [47]=2ºIvs2ºH
                // R16: 49=1ºAvs G(34) | 50=G(35)vs1ºH | 51=1ºEvs G(38) | 52=G(39)vs1ºD | 53=1ºCvs G(42) | 54=G(43)vs1ºF | 55=1ºGvs G(46) | 56=G(47)vs1ºB
                // QF: G(49)vsG(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vsG(56)[60]
                // Nota: con 12 zonas, 4 primeros juegan R32 (1ºI, 1ºL, 1ºK, 1ºJ)
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → R16/49 lado 1
                    19, 22,   // 2ºG vs 2ºJ  → prelim [34]  → R16/49 lado 2
                    9,  14,   // 1ºI vs 2ºB  → prelim [35]  → R16/50 lado 1
                    b2,  8,   // BYE vs 1ºH  → R16/50 lado 2  [R16/50 = G(35) vs 1ºH]
                    b3,  5,   // BYE vs 1ºE  → R16/51 lado 1
                    15, 12,   // 2ºC vs 1ºL  → prelim [38]  → R16/51 lado 2  [R16/51 = 1ºE vs G(38)]
                    23, 18,   // 2ºK vs 2ºF  → prelim [39]  → R16/52 lado 1
                    b4,  4,   // BYE vs 1ºD  → R16/52 lado 2  [R16/52 = G(39) vs 1ºD]
                    3,  b5,   // 1ºC vs BYE  → R16/53 lado 1
                    17, 24,   // 2ºE vs 2ºL  → prelim [42]  → R16/53 lado 2  [R16/53 = 1ºC vs G(42)]
                    11, 16,   // 1ºK vs 2ºD  → prelim [43]  → R16/54 lado 1
                    b6,  6,   // BYE vs 1ºF  → R16/54 lado 2  [R16/54 = G(43) vs 1ºF]
                    b7,  7,   // BYE vs 1ºG  → R16/55 lado 1
                    13, 10,   // 2ºA vs 1ºJ  → prelim [46]  → R16/55 lado 2  [R16/55 = 1ºG vs G(46)]
                    21, 20,   // 2ºI vs 2ºH  → prelim [47]  → R16/56 lado 1
                    b8,  2    // BYE vs 1ºB  → R16/56 lado 2  [R16/56 = G(47) vs 1ºB]
                ));
            } else if (numClassified == 25 && totalPairs == 37) {
                // FAP — 37 inscriptos (1Z×4+11Z×3 → 12+12+1=25 clasificados, 7 BYEs)
                // seeds: 1-12=1°(A-L), 13-24=2°(A-L), 25=3ºA
                // R32: 9 partidos reales: [34]=2ºGvs2ºJ | [35]=1ºIvs2ºB | [36]=3ºAvs1ºH | [38]=2ºCvs1ºL | [39]=2ºKvs2ºF | [42]=2ºEvs2ºL | [43]=1ºKvs2ºD | [46]=2ºAvs1ºJ | [47]=2ºIvs2ºH
                // R16: 49=1ºAvs G(34) | 50=G(35)vs G(36) | 51=1ºEvs G(38) | 52=G(39)vs1ºD | 53=1ºCvs G(42) | 54=G(43)vs1ºF | 55=1ºGvs G(46) | 56=G(47)vs1ºB
                // QF: G(49)vsG(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vsG(56)[60]
                // Nota: R16/50 sin BYE directo — match 36 activo (3ºA vs 1ºH)
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → R16/49 lado 1
                    19, 22,   // 2ºG vs 2ºJ  → prelim [34]  → R16/49 lado 2
                    9,  14,   // 1ºI vs 2ºB  → prelim [35]  → R16/50 lado 1
                    25,  8,   // 3ºA vs 1ºH  → prelim [36]  → R16/50 lado 2  [R16/50 = G(35) vs G(36)]
                    b2,  5,   // BYE vs 1ºE  → R16/51 lado 1
                    15, 12,   // 2ºC vs 1ºL  → prelim [38]  → R16/51 lado 2  [R16/51 = 1ºE vs G(38)]
                    23, 18,   // 2ºK vs 2ºF  → prelim [39]  → R16/52 lado 1
                    b3,  4,   // BYE vs 1ºD  → R16/52 lado 2  [R16/52 = G(39) vs 1ºD]
                    3,  b4,   // 1ºC vs BYE  → R16/53 lado 1
                    17, 24,   // 2ºE vs 2ºL  → prelim [42]  → R16/53 lado 2  [R16/53 = 1ºC vs G(42)]
                    11, 16,   // 1ºK vs 2ºD  → prelim [43]  → R16/54 lado 1
                    b5,  6,   // BYE vs 1ºF  → R16/54 lado 2  [R16/54 = G(43) vs 1ºF]
                    b6,  7,   // BYE vs 1ºG  → R16/55 lado 1
                    13, 10,   // 2ºA vs 1ºJ  → prelim [46]  → R16/55 lado 2  [R16/55 = 1ºG vs G(46)]
                    21, 20,   // 2ºI vs 2ºH  → prelim [47]  → R16/56 lado 1
                    b7,  2    // BYE vs 1ºB  → R16/56 lado 2  [R16/56 = G(47) vs 1ºB]
                ));
            } else if (numClassified == 26 && totalPairs == 38) {
                // FAP — 38 inscriptos (2Z×4+10Z×3 → 12+12+2=26 clasificados, 6 BYEs)
                // seeds: 1-12=1°(A-L), 13-24=2°(A-L), 25=3ºA, 26=3ºB
                // R32: 10 partidos reales: [34]=2ºGvs2ºJ | [35]=1ºIvs2ºB | [36]=3ºAvs1ºH | [38]=2ºCvs1ºL | [39]=2ºKvs2ºF | [42]=2ºEvs2ºL | [43]=1ºKvs2ºD | [45]=1ºGvs3ºB | [46]=2ºAvs1ºJ | [47]=2ºIvs2ºH
                // R16: 49=1ºAvs G(34) | 50=G(35)vs G(36) | 51=1ºEvs G(38) | 52=G(39)vs1ºD | 53=1ºCvs G(42) | 54=G(43)vs1ºF | 55=G(45)vs G(46) | 56=G(47)vs1ºB
                // QF: G(49)vsG(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vsG(56)[60]
                // Nota: R16/55 también sin BYE directo — match 45 activo (1ºG vs 3ºB)
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → R16/49 lado 1
                    19, 22,   // 2ºG vs 2ºJ  → prelim [34]  → R16/49 lado 2
                    9,  14,   // 1ºI vs 2ºB  → prelim [35]  → R16/50 lado 1
                    25,  8,   // 3ºA vs 1ºH  → prelim [36]  → R16/50 lado 2  [R16/50 = G(35) vs G(36)]
                    b2,  5,   // BYE vs 1ºE  → R16/51 lado 1
                    15, 12,   // 2ºC vs 1ºL  → prelim [38]  → R16/51 lado 2  [R16/51 = 1ºE vs G(38)]
                    23, 18,   // 2ºK vs 2ºF  → prelim [39]  → R16/52 lado 1
                    b3,  4,   // BYE vs 1ºD  → R16/52 lado 2  [R16/52 = G(39) vs 1ºD]
                    3,  b4,   // 1ºC vs BYE  → R16/53 lado 1
                    17, 24,   // 2ºE vs 2ºL  → prelim [42]  → R16/53 lado 2  [R16/53 = 1ºC vs G(42)]
                    11, 16,   // 1ºK vs 2ºD  → prelim [43]  → R16/54 lado 1
                    b5,  6,   // BYE vs 1ºF  → R16/54 lado 2  [R16/54 = G(43) vs 1ºF]
                    7,  26,   // 1ºG vs 3ºB  → prelim [45]  → R16/55 lado 1
                    13, 10,   // 2ºA vs 1ºJ  → prelim [46]  → R16/55 lado 2  [R16/55 = G(45) vs G(46)]
                    21, 20,   // 2ºI vs 2ºH  → prelim [47]  → R16/56 lado 1
                    b6,  2    // BYE vs 1ºB  → R16/56 lado 2  [R16/56 = G(47) vs 1ºB]
                ));
            } else if (numClassified == 26 && totalPairs == 39) {
                // FAP — 39 inscriptos (13Z×3 → 13+13=26 clasificados, 6 BYEs — sin terceros)
                // seeds: 1-13=1°(A-M), 14-26=2°(A-M)
                // R32: 10 partidos reales: [34]=2ºJvs2ºK | [35]=1ºIvs2ºC | [36]=2ºBvs1ºH | [38]=2ºFvs1ºL | [39]=1ºMvs2ºG | [42]=2ºHvs2ºM | [43]=1ºKvs2ºE | [45]=1ºGvs2ºA | [46]=2ºDvs1ºJ | [47]=2ºLvs2ºI
                // R16: 49=1ºAvs G(34) | 50=G(35)vs G(36) | 51=1ºEvs G(38) | 52=G(39)vs1ºD | 53=1ºCvs G(42) | 54=G(43)vs1ºF | 55=G(45)vs G(46) | 56=G(47)vs1ºB
                // QF: G(49)vsG(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vsG(56)[60]
                // Nota: 13 zonas, 5 primeros juegan R32 (1ºI, 1ºH, 1ºL, 1ºM, 1ºK, 1ºG, 1ºJ = 7 primeros en prelims)
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → R16/49 lado 1
                    23, 24,   // 2ºJ vs 2ºK  → prelim [34]  → R16/49 lado 2
                    9,  16,   // 1ºI vs 2ºC  → prelim [35]  → R16/50 lado 1
                    15,  8,   // 2ºB vs 1ºH  → prelim [36]  → R16/50 lado 2  [R16/50 = G(35) vs G(36)]
                    b2,  5,   // BYE vs 1ºE  → R16/51 lado 1
                    19, 12,   // 2ºF vs 1ºL  → prelim [38]  → R16/51 lado 2  [R16/51 = 1ºE vs G(38)]
                    13, 20,   // 1ºM vs 2ºG  → prelim [39]  → R16/52 lado 1
                    b3,  4,   // BYE vs 1ºD  → R16/52 lado 2  [R16/52 = G(39) vs 1ºD]
                    3,  b4,   // 1ºC vs BYE  → R16/53 lado 1
                    21, 26,   // 2ºH vs 2ºM  → prelim [42]  → R16/53 lado 2  [R16/53 = 1ºC vs G(42)]
                    11, 18,   // 1ºK vs 2ºE  → prelim [43]  → R16/54 lado 1
                    b5,  6,   // BYE vs 1ºF  → R16/54 lado 2  [R16/54 = G(43) vs 1ºF]
                    7,  14,   // 1ºG vs 2ºA  → prelim [45]  → R16/55 lado 1
                    17, 10,   // 2ºD vs 1ºJ  → prelim [46]  → R16/55 lado 2  [R16/55 = G(45) vs G(46)]
                    25, 22,   // 2ºL vs 2ºI  → prelim [47]  → R16/56 lado 1
                    b6,  2    // BYE vs 1ºB  → R16/56 lado 2  [R16/56 = G(47) vs 1ºB]
                ));
            } else if (numClassified == 27 && totalPairs == 40) {
                // FAP — 40 inscriptos (1Z×4+12Z×3 → 13+13+1=27 clasificados, 5 BYEs)
                // seeds: 1-13=1°(A-M), 14-26=2°(A-M), 27=3ºA
                // R32: 11 partidos reales: [34]=2ºJvs2ºK | [35]=1ºIvs2ºC | [36]=2ºBvs1ºH | [38]=2ºFvs1ºL | [39]=1ºMvs2ºG | [42]=2ºHvs2ºM | [43]=1ºKvs2ºE | [44]=3ºAvs1ºF | [45]=1ºGvs2ºA | [46]=2ºDvs1ºJ | [47]=2ºLvs2ºI
                // R16: 49=1ºAvs G(34) | 50=G(35)vs G(36) | 51=1ºEvs G(38) | 52=G(39)vs1ºD | 53=1ºCvs G(42) | 54=G(43)vs G(44) | 55=G(45)vs G(46) | 56=G(47)vs1ºB
                // QF: G(49)vsG(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vsG(56)[60]
                // Nota: R16/54 sin BYE directo en ningún lado — G(43)=[1ºK/2ºE] vs G(44)=[3ºA/1ºF]
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → R16/49 lado 1
                    23, 24,   // 2ºJ vs 2ºK  → prelim [34]  → R16/49 lado 2
                    9,  16,   // 1ºI vs 2ºC  → prelim [35]  → R16/50 lado 1
                    15,  8,   // 2ºB vs 1ºH  → prelim [36]  → R16/50 lado 2  [R16/50 = G(35) vs G(36)]
                    b2,  5,   // BYE vs 1ºE  → R16/51 lado 1
                    19, 12,   // 2ºF vs 1ºL  → prelim [38]  → R16/51 lado 2  [R16/51 = 1ºE vs G(38)]
                    13, 20,   // 1ºM vs 2ºG  → prelim [39]  → R16/52 lado 1
                    b3,  4,   // BYE vs 1ºD  → R16/52 lado 2  [R16/52 = G(39) vs 1ºD]
                    3,  b4,   // 1ºC vs BYE  → R16/53 lado 1
                    21, 26,   // 2ºH vs 2ºM  → prelim [42]  → R16/53 lado 2  [R16/53 = 1ºC vs G(42)]
                    11, 18,   // 1ºK vs 2ºE  → prelim [43]  → R16/54 lado 1
                    27,  6,   // 3ºA vs 1ºF  → prelim [44]  → R16/54 lado 2  [R16/54 = G(43) vs G(44)]
                    7,  14,   // 1ºG vs 2ºA  → prelim [45]  → R16/55 lado 1
                    17, 10,   // 2ºD vs 1ºJ  → prelim [46]  → R16/55 lado 2  [R16/55 = G(45) vs G(46)]
                    25, 22,   // 2ºL vs 2ºI  → prelim [47]  → R16/56 lado 1
                    b5,  2    // BYE vs 1ºB  → R16/56 lado 2  [R16/56 = G(47) vs 1ºB]
                ));
            } else if (numClassified == 28 && totalPairs == 41) {
                // FAP — 41 inscriptos (2Z×4+11Z×3 → 13+13+2=28 clasificados, 4 BYEs)
                // seeds: 1-13=1°(A-M), 14-26=2°(A-M), 27=3ºA, 28=3ºB
                // R32: 12 partidos reales: [34]=2ºJvs2ºK | [35]=1ºIvs2ºC | [36]=2ºBvs1ºH | [37]=1ºEvs3ºB | [38]=2ºFvs1ºL | [39]=1ºMvs2ºG | [42]=2ºHvs2ºM | [43]=1ºKvs2ºE | [44]=3ºAvs1ºF | [45]=1ºGvs2ºA | [46]=2ºDvs1ºJ | [47]=2ºLvs2ºI
                // R16: 49=1ºAvs G(34) | 50=G(35)vs G(36) | 51=G(37)vs G(38) | 52=G(39)vs1ºD | 53=1ºCvs G(42) | 54=G(43)vs G(44) | 55=G(45)vs G(46) | 56=G(47)vs1ºB
                // QF: G(49)vsG(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vsG(56)[60]
                // Nota: R16/51 también sin BYE — match 37 activo (1ºE vs 3ºB); solo 4 BYEs restantes
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → R16/49 lado 1
                    23, 24,   // 2ºJ vs 2ºK  → prelim [34]  → R16/49 lado 2
                    9,  16,   // 1ºI vs 2ºC  → prelim [35]  → R16/50 lado 1
                    15,  8,   // 2ºB vs 1ºH  → prelim [36]  → R16/50 lado 2  [R16/50 = G(35) vs G(36)]
                    5,  28,   // 1ºE vs 3ºB  → prelim [37]  → R16/51 lado 1
                    19, 12,   // 2ºF vs 1ºL  → prelim [38]  → R16/51 lado 2  [R16/51 = G(37) vs G(38)]
                    13, 20,   // 1ºM vs 2ºG  → prelim [39]  → R16/52 lado 1
                    b2,  4,   // BYE vs 1ºD  → R16/52 lado 2  [R16/52 = G(39) vs 1ºD]
                    3,  b3,   // 1ºC vs BYE  → R16/53 lado 1
                    21, 26,   // 2ºH vs 2ºM  → prelim [42]  → R16/53 lado 2  [R16/53 = 1ºC vs G(42)]
                    11, 18,   // 1ºK vs 2ºE  → prelim [43]  → R16/54 lado 1
                    27,  6,   // 3ºA vs 1ºF  → prelim [44]  → R16/54 lado 2  [R16/54 = G(43) vs G(44)]
                    7,  14,   // 1ºG vs 2ºA  → prelim [45]  → R16/55 lado 1
                    17, 10,   // 2ºD vs 1ºJ  → prelim [46]  → R16/55 lado 2  [R16/55 = G(45) vs G(46)]
                    25, 22,   // 2ºL vs 2ºI  → prelim [47]  → R16/56 lado 1
                    b4,  2    // BYE vs 1ºB  → R16/56 lado 2  [R16/56 = G(47) vs 1ºB]
                ));
            } else if (numClassified == 28 && totalPairs == 42) {
                // FAP — 42 inscriptos (14Z×3 → 14+14=28 clasificados, 4 BYEs — sin terceros)
                // seeds: 1-14=1°(A-N), 15-28=2°(A-N)
                // R32: 12 partidos reales: [34]=2ºNvs2ºK | [35]=1ºIvs2ºF | [36]=2ºCvs1ºH | [37]=1ºEvs2ºB | [38]=2ºGvs1ºL | [39]=1ºMvs2ºJ | [42]=2ºIvs1ºN | [43]=1ºKvs2ºH | [44]=2ºAvs1ºF | [45]=1ºGvs2ºD | [46]=2ºEvs1ºJ | [47]=2ºMvs2ºL
                // R16: 49=1ºAvs G(34) | 50=G(35)vs G(36) | 51=G(37)vs G(38) | 52=G(39)vs1ºD | 53=1ºCvs G(42) | 54=G(43)vs G(44) | 55=G(45)vs G(46) | 56=G(47)vs1ºB
                // QF: G(49)vsG(50)[57] | G(51)vsG(52)[58] | G(53)vsG(54)[59] | G(55)vsG(56)[60]
                // Nota: 14 zonas sin terceros; seeds 1-14 firsts, 15-28 seconds
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → R16/49 lado 1
                    28, 25,   // 2ºN vs 2ºK  → prelim [34]  → R16/49 lado 2
                    9,  20,   // 1ºI vs 2ºF  → prelim [35]  → R16/50 lado 1
                    17,  8,   // 2ºC vs 1ºH  → prelim [36]  → R16/50 lado 2  [R16/50 = G(35) vs G(36)]
                    5,  16,   // 1ºE vs 2ºB  → prelim [37]  → R16/51 lado 1
                    21, 12,   // 2ºG vs 1ºL  → prelim [38]  → R16/51 lado 2  [R16/51 = G(37) vs G(38)]
                    13, 24,   // 1ºM vs 2ºJ  → prelim [39]  → R16/52 lado 1
                    b2,  4,   // BYE vs 1ºD  → R16/52 lado 2  [R16/52 = G(39) vs 1ºD]
                    3,  b3,   // 1ºC vs BYE  → R16/53 lado 1
                    23, 14,   // 2ºI vs 1ºN  → prelim [42]  → R16/53 lado 2  [R16/53 = 1ºC vs G(42)]
                    11, 22,   // 1ºK vs 2ºH  → prelim [43]  → R16/54 lado 1
                    15,  6,   // 2ºA vs 1ºF  → prelim [44]  → R16/54 lado 2  [R16/54 = G(43) vs G(44)]
                    7,  18,   // 1ºG vs 2ºD  → prelim [45]  → R16/55 lado 1
                    19, 10,   // 2ºE vs 1ºJ  → prelim [46]  → R16/55 lado 2  [R16/55 = G(45) vs G(46)]
                    27, 26,   // 2ºM vs 2ºL  → prelim [47]  → R16/56 lado 1
                    b4,  2    // BYE vs 1ºB  → R16/56 lado 2  [R16/56 = G(47) vs 1ºB]
                ));
            } else if (numClassified == 29 && totalPairs == 43) {
                // FAP — 43 inscriptos (1Z×4+13Z×3 → 14+14+1=29 clasificados, 3 BYEs)
                // seeds: 1-14=1°(A-N), 15-28=2°(A-N), 29=3°A
                // R32: [34]=2ºNvs2ºK | [35]=1ºIvs2ºF | [36]=2ºCvs1ºH | [37]=1ºEvs2ºB | [38]=2ºGvs1ºL | [39]=1ºMvs2ºJ | [40]=3ºAvs1ºD | [42]=2ºIvs1ºN | [43]=1ºKvs2ºH | [44]=2ºAvs1ºF | [45]=1ºGvs2ºD | [46]=2ºEvs1ºJ | [47]=2ºMvs2ºL
                // R16: 49=1ºAvs G(34) | 50=G(35)vs G(36) | 51=G(37)vs G(38) | 52=G(39)vs G(40) | 53=1ºCvs G(42) | 54=G(43)vs G(44) | 55=G(45)vs G(46) | 56=G(47)vs1ºB
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → R16/49 lado 1
                    28, 25,   // 2ºN vs 2ºK  → prelim [34]  → R16/49 lado 2
                    9,  20,   // 1ºI vs 2ºF  → prelim [35]  → R16/50 lado 1
                    17,  8,   // 2ºC vs 1ºH  → prelim [36]  → R16/50 lado 2
                    5,  16,   // 1ºE vs 2ºB  → prelim [37]  → R16/51 lado 1
                    21, 12,   // 2ºG vs 1ºL  → prelim [38]  → R16/51 lado 2
                    13, 24,   // 1ºM vs 2ºJ  → prelim [39]  → R16/52 lado 1
                    29,  4,   // 3ºA vs 1ºD  → prelim [40]  → R16/52 lado 2
                    3,  b2,   // 1ºC vs BYE  → R16/53 lado 1
                    23, 14,   // 2ºI vs 1ºN  → prelim [42]  → R16/53 lado 2
                    11, 22,   // 1ºK vs 2ºH  → prelim [43]  → R16/54 lado 1
                    15,  6,   // 2ºA vs 1ºF  → prelim [44]  → R16/54 lado 2
                    7,  18,   // 1ºG vs 2ºD  → prelim [45]  → R16/55 lado 1
                    19, 10,   // 2ºE vs 1ºJ  → prelim [46]  → R16/55 lado 2
                    27, 26,   // 2ºM vs 2ºL  → prelim [47]  → R16/56 lado 1
                    b3,  2    // BYE vs 1ºB  → R16/56 lado 2
                ));
            } else if (numClassified == 30 && totalPairs == 44) {
                // FAP — 44 inscriptos (2Z×4+12Z×3 → 14+14+2=30 clasificados, 2 BYEs)
                // seeds: 1-14=1°(A-N), 15-28=2°(A-N), 29=3°A, 30=3°B
                // R32: [34]=2ºNvs2ºK | [35]=1ºIvs2ºF | [36]=2ºCvs1ºH | [37]=1ºEvs2ºB | [38]=2ºGvs1ºL | [39]=1ºMvs2ºJ | [40]=3ºAvs1ºD | [41]=1ºCvs3ºB | [42]=2ºIvs1ºN | [43]=1ºKvs2ºH | [44]=2ºAvs1ºF | [45]=1ºGvs2ºD | [46]=2ºEvs1ºJ | [47]=2ºMvs2ºL
                // R16: 49=1ºAvs G(34) | 50=G(35)vs G(36) | 51=G(37)vs G(38) | 52=G(39)vs G(40) | 53=G(41)vs G(42) | 54=G(43)vs G(44) | 55=G(45)vs G(46) | 56=G(47)vs1ºB
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → R16/49 lado 1
                    28, 25,   // 2ºN vs 2ºK  → prelim [34]  → R16/49 lado 2
                    9,  20,   // 1ºI vs 2ºF  → prelim [35]  → R16/50 lado 1
                    17,  8,   // 2ºC vs 1ºH  → prelim [36]  → R16/50 lado 2
                    5,  16,   // 1ºE vs 2ºB  → prelim [37]  → R16/51 lado 1
                    21, 12,   // 2ºG vs 1ºL  → prelim [38]  → R16/51 lado 2
                    13, 24,   // 1ºM vs 2ºJ  → prelim [39]  → R16/52 lado 1
                    29,  4,   // 3ºA vs 1ºD  → prelim [40]  → R16/52 lado 2
                    3,  30,   // 1ºC vs 3ºB  → prelim [41]  → R16/53 lado 1
                    23, 14,   // 2ºI vs 1ºN  → prelim [42]  → R16/53 lado 2
                    11, 22,   // 1ºK vs 2ºH  → prelim [43]  → R16/54 lado 1
                    15,  6,   // 2ºA vs 1ºF  → prelim [44]  → R16/54 lado 2
                    7,  18,   // 1ºG vs 2ºD  → prelim [45]  → R16/55 lado 1
                    19, 10,   // 2ºE vs 1ºJ  → prelim [46]  → R16/55 lado 2
                    27, 26,   // 2ºM vs 2ºL  → prelim [47]  → R16/56 lado 1
                    b2,  2    // BYE vs 1ºB  → R16/56 lado 2
                ));
            } else if (numClassified == 30 && totalPairs == 45) {
                // FAP — 45 inscriptos (15Z×3 → 15+15=30 clasificados, 2 BYEs — sin terceros)
                // seeds: 1-15=1°(A-O), 16-30=2°(A-O)
                // R32: [34]=2ºNvs2ºO | [35]=1ºIvs2ºG | [36]=2ºFvs1ºH | [37]=1ºEvs2ºC | [38]=2ºJvs1ºL | [39]=1ºMvs2ºK | [40]=2ºBvs1ºD | [41]=1ºCvs2ºA | [42]=2ºLvs1ºN | [43]=1ºKvs2ºI | [44]=2ºDvs1ºF | [45]=1ºGvs2ºE | [46]=2ºHvs1ºJ | [47]=1ºOvs2ºM
                // R16: 49=1ºAvs G(34) | 50=G(35)vs G(36) | 51=G(37)vs G(38) | 52=G(39)vs G(40) | 53=G(41)vs G(42) | 54=G(43)vs G(44) | 55=G(45)vs G(46) | 56=G(47)vs1ºB
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → R16/49 lado 1
                    29, 30,   // 2ºN vs 2ºO  → prelim [34]  → R16/49 lado 2
                    9,  22,   // 1ºI vs 2ºG  → prelim [35]  → R16/50 lado 1
                    21,  8,   // 2ºF vs 1ºH  → prelim [36]  → R16/50 lado 2
                    5,  18,   // 1ºE vs 2ºC  → prelim [37]  → R16/51 lado 1
                    25, 12,   // 2ºJ vs 1ºL  → prelim [38]  → R16/51 lado 2
                    13, 26,   // 1ºM vs 2ºK  → prelim [39]  → R16/52 lado 1
                    17,  4,   // 2ºB vs 1ºD  → prelim [40]  → R16/52 lado 2
                    3,  16,   // 1ºC vs 2ºA  → prelim [41]  → R16/53 lado 1
                    27, 14,   // 2ºL vs 1ºN  → prelim [42]  → R16/53 lado 2
                    11, 24,   // 1ºK vs 2ºI  → prelim [43]  → R16/54 lado 1
                    19,  6,   // 2ºD vs 1ºF  → prelim [44]  → R16/54 lado 2
                    7,  20,   // 1ºG vs 2ºE  → prelim [45]  → R16/55 lado 1
                    23, 10,   // 2ºH vs 1ºJ  → prelim [46]  → R16/55 lado 2
                    15, 28,   // 1ºO vs 2ºM  → prelim [47]  → R16/56 lado 1
                    b2,  2    // BYE vs 1ºB  → R16/56 lado 2
                ));
            } else if (numClassified == 31 && totalPairs == 46) {
                // FAP — 46 inscriptos (1Z×4+14Z×3 → 15+15+1=31 clasificados, 1 BYE)
                // seeds: 1-15=1°(A-O), 16-30=2°(A-O), 31=3°A
                // R32: solo 1ºA tiene BYE (match 33). Todos los demás son prelims reales.
                // [34]=2ºNvs2ºO | [35]=1ºIvs2ºG | [36]=2ºFvs1ºH | [37]=1ºEvs2ºC | [38]=2ºJvs1ºL | [39]=1ºMvs2ºK | [40]=2ºBvs1ºD | [41]=1ºCvs2ºA | [42]=2ºLvs1ºN | [43]=1ºKvs2ºI | [44]=2ºDvs1ºF | [45]=1ºGvs2ºE | [46]=2ºHvs1ºJ | [47]=1ºOvs2ºM | [48]=3ºAvs1ºB
                // R16: 49=1ºAvs G(34) | 50=G(35)vs G(36) | 51=G(37)vs G(38) | 52=G(39)vs G(40) | 53=G(41)vs G(42) | 54=G(43)vs G(44) | 55=G(45)vs G(46) | 56=G(47)vs G(48)
                return new ArrayList<>(Arrays.asList(
                    1,  b1,   // 1ºA vs BYE  → R16/49 lado 1
                    29, 30,   // 2ºN vs 2ºO  → prelim [34]  → R16/49 lado 2
                    9,  22,   // 1ºI vs 2ºG  → prelim [35]  → R16/50 lado 1
                    21,  8,   // 2ºF vs 1ºH  → prelim [36]  → R16/50 lado 2
                    5,  18,   // 1ºE vs 2ºC  → prelim [37]  → R16/51 lado 1
                    25, 12,   // 2ºJ vs 1ºL  → prelim [38]  → R16/51 lado 2
                    13, 26,   // 1ºM vs 2ºK  → prelim [39]  → R16/52 lado 1
                    17,  4,   // 2ºB vs 1ºD  → prelim [40]  → R16/52 lado 2
                    3,  16,   // 1ºC vs 2ºA  → prelim [41]  → R16/53 lado 1
                    27, 14,   // 2ºL vs 1ºN  → prelim [42]  → R16/53 lado 2
                    11, 24,   // 1ºK vs 2ºI  → prelim [43]  → R16/54 lado 1
                    19,  6,   // 2ºD vs 1ºF  → prelim [44]  → R16/54 lado 2
                    7,  20,   // 1ºG vs 2ºE  → prelim [45]  → R16/55 lado 1
                    23, 10,   // 2ºH vs 1ºJ  → prelim [46]  → R16/55 lado 2
                    15, 28,   // 1ºO vs 2ºM  → prelim [47]  → R16/56 lado 1
                    31,  2    // 3ºA vs 1ºB  → prelim [48]  → R16/56 lado 2
                ));
            } else if (numClassified == 32 && totalPairs == 47) {
                // FAP — 47 inscriptos (2Z×4+13Z×3 → 15+15+2=32 clasificados, 0 BYEs — bracket completo)
                // seeds: 1-15=1°(A-O), 16-30=2°(A-O), 31=3°A, 32=3°B
                // R32: todos prelims reales; [33]=1ºAvs3ºB | [34]=2ºNvs2ºO | [35]=1ºIvs2ºG | [36]=2ºFvs1ºH | [37]=1ºEvs2ºC | [38]=2ºJvs1ºL | [39]=1ºMvs2ºK | [40]=2ºBvs1ºD | [41]=1ºCvs2ºA | [42]=2ºLvs1ºN | [43]=1ºKvs2ºI | [44]=2ºDvs1ºF | [45]=1ºGvs2ºE | [46]=2ºHvs1ºJ | [47]=1ºOvs2ºM | [48]=3ºAvs1ºB
                // R16: 49=G(33)vs G(34) | 50=G(35)vs G(36) | 51=G(37)vs G(38) | 52=G(39)vs G(40) | 53=G(41)vs G(42) | 54=G(43)vs G(44) | 55=G(45)vs G(46) | 56=G(47)vs G(48)
                return new ArrayList<>(Arrays.asList(
                    1,  32,   // 1ºA vs 3ºB  → prelim [33]  → R16/49 lado 1
                    29, 30,   // 2ºN vs 2ºO  → prelim [34]  → R16/49 lado 2
                    9,  22,   // 1ºI vs 2ºG  → prelim [35]  → R16/50 lado 1
                    21,  8,   // 2ºF vs 1ºH  → prelim [36]  → R16/50 lado 2
                    5,  18,   // 1ºE vs 2ºC  → prelim [37]  → R16/51 lado 1
                    25, 12,   // 2ºJ vs 1ºL  → prelim [38]  → R16/51 lado 2
                    13, 26,   // 1ºM vs 2ºK  → prelim [39]  → R16/52 lado 1
                    17,  4,   // 2ºB vs 1ºD  → prelim [40]  → R16/52 lado 2
                    3,  16,   // 1ºC vs 2ºA  → prelim [41]  → R16/53 lado 1
                    27, 14,   // 2ºL vs 1ºN  → prelim [42]  → R16/53 lado 2
                    11, 24,   // 1ºK vs 2ºI  → prelim [43]  → R16/54 lado 1
                    19,  6,   // 2ºD vs 1ºF  → prelim [44]  → R16/54 lado 2
                    7,  20,   // 1ºG vs 2ºE  → prelim [45]  → R16/55 lado 1
                    23, 10,   // 2ºH vs 1ºJ  → prelim [46]  → R16/55 lado 2
                    15, 28,   // 1ºO vs 2ºM  → prelim [47]  → R16/56 lado 1
                    31,  2    // 3ºA vs 1ºB  → prelim [48]  → R16/56 lado 2
                ));
            } else if (numClassified == 32 && totalPairs == 48) {
                // FAP — 48 inscriptos (16Z×3 → 16+16=32 clasificados, 0 BYEs — bracket completo, sin terceros)
                // seeds: 1-16=1°(A-P), 17-32=2°(A-P)
                // R32: todos prelims reales; [33]=1ºAvs2ºB | [34]=2ºOvs1ºP | [35]=1ºIvs2ºJ | [36]=2ºGvs1ºH | [37]=1ºEvs2ºF | [38]=2ºKvs1ºL | [39]=1ºMvs2ºN | [40]=2ºCvs1ºD | [41]=1ºCvs2ºD | [42]=2ºMvs1ºN | [43]=1ºKvs2ºL | [44]=2ºEvs1ºF | [45]=1ºGvs2ºH | [46]=2ºIvs1ºJ | [47]=1ºOvs2ºP | [48]=2ºAvs1ºB
                // R16: 49=G(33)vs G(34) | 50=G(35)vs G(36) | 51=G(37)vs G(38) | 52=G(39)vs G(40) | 53=G(41)vs G(42) | 54=G(43)vs G(44) | 55=G(45)vs G(46) | 56=G(47)vs G(48)
                return new ArrayList<>(Arrays.asList(
                    1,  18,   // 1ºA vs 2ºB  → prelim [33]  → R16/49 lado 1
                    31, 16,   // 2ºO vs 1ºP  → prelim [34]  → R16/49 lado 2
                    9,  26,   // 1ºI vs 2ºJ  → prelim [35]  → R16/50 lado 1
                    23,  8,   // 2ºG vs 1ºH  → prelim [36]  → R16/50 lado 2
                    5,  22,   // 1ºE vs 2ºF  → prelim [37]  → R16/51 lado 1
                    27, 12,   // 2ºK vs 1ºL  → prelim [38]  → R16/51 lado 2
                    13, 30,   // 1ºM vs 2ºN  → prelim [39]  → R16/52 lado 1
                    19,  4,   // 2ºC vs 1ºD  → prelim [40]  → R16/52 lado 2
                    3,  20,   // 1ºC vs 2ºD  → prelim [41]  → R16/53 lado 1
                    29, 14,   // 2ºM vs 1ºN  → prelim [42]  → R16/53 lado 2
                    11, 28,   // 1ºK vs 2ºL  → prelim [43]  → R16/54 lado 1
                    21,  6,   // 2ºE vs 1ºF  → prelim [44]  → R16/54 lado 2
                    7,  24,   // 1ºG vs 2ºH  → prelim [45]  → R16/55 lado 1
                    25, 10,   // 2ºI vs 1ºJ  → prelim [46]  → R16/55 lado 2
                    15, 32,   // 1ºO vs 2ºP  → prelim [47]  → R16/56 lado 1
                    17,  2    // 2ºA vs 1ºB  → prelim [48]  → R16/56 lado 2
                ));
            }
            // Fallback para configuraciones no mapeadas en bracket de 32
            return generateStandardSeedOrder(bracketSize);
        }

        // Fallback para brackets mayores
        return generateStandardSeedOrder(bracketSize);
    }

    /** Algoritmo estándar de seeding (tennis-style), usado como fallback. */
    private List<Integer> generateStandardSeedOrder(int n) {
        if (n == 2) return new ArrayList<>(Arrays.asList(1, 2));
        List<Integer> half = generateStandardSeedOrder(n / 2);
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
        EliminationMatchDto.EliminationMatchDtoBuilder builder = EliminationMatchDto.builder()
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
                .status(match.getStatus());

        if (match.getStatus() == com.padeladmin.padeladmin.enums.MatchStatus.PLAYED) {
            matchResultRepository.findByMatchId(match.getId()).ifPresent(result -> {
                builder.winnerPairId(result.getWinnerPair().getId());
                builder.sets(result.getSets().stream()
                        .map(s -> com.padeladmin.padeladmin.dto.fixture.MatchResponseDto.SetScoreDto.builder()
                                .pair1Games(s.getPair1Games())
                                .pair2Games(s.getPair2Games())
                                .build())
                        .toList());
            });
        }

        return builder.build();
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

    // ── Record interno ────────────────────────────────────────────────────────

    record ClassifiedPairInfo(Long pairId, Long zoneId, String zoneName, int zonePosition) {}
}
