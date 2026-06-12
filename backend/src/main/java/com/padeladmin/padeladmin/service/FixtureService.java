package com.padeladmin.padeladmin.service;

import com.padeladmin.padeladmin.dto.fixture.FixtureResponseDto;
import com.padeladmin.padeladmin.dto.fixture.MatchPairDto;
import com.padeladmin.padeladmin.dto.fixture.MatchPlacementDto;
import com.padeladmin.padeladmin.dto.fixture.MatchResponseDto;
import com.padeladmin.padeladmin.dto.fixture.ReorganizeResultDto;
import com.padeladmin.padeladmin.entity.*;
import com.padeladmin.padeladmin.enums.ConstraintType;
import com.padeladmin.padeladmin.enums.MatchPhase;
import com.padeladmin.padeladmin.enums.ZoneRound2Type;
import com.padeladmin.padeladmin.enums.MatchStatus;
import com.padeladmin.padeladmin.exception.BusinessException;
import com.padeladmin.padeladmin.exception.ResourceNotFoundException;
import com.padeladmin.padeladmin.model.TimeSlot;
import com.padeladmin.padeladmin.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FixtureService {

    private final TournamentService tournamentService;
    private final TournamentRepository tournamentRepository;
    private final ZoneRepository zoneRepository;
    private final ZonePairRepository zonePairRepository;
    private final PairPlayerRepository pairPlayerRepository;
    private final MatchRepository matchRepository;
    private final MatchResultRepository matchResultRepository;
    private final CourtRepository courtRepository;
    private final CourtAvailabilityRepository courtAvailabilityRepository;
    private final TournamentBufferRepository bufferRepository;
    private final PairScheduleConstraintRepository constraintRepository;

    // Incremento en minutos para generar slots horarios
    private static final int SLOT_STEP_MINUTES = 30;

    // ── Generación del fixture ────────────────────────────────────────────────

    @Transactional
    public FixtureResponseDto generateFixture(Long tournamentId) {
        Tournament tournament = getTournamentOrThrow(tournamentId);

        if (tournament.getComplex() == null) {
            throw new BusinessException("El torneo no tiene un complejo asignado");
        }

        // 1. Eliminar partidos de zona existentes (solo si no hay partidos jugados)
        List<Match> existingMatches = matchRepository
                .findByTournamentIdAndPhase(tournamentId, MatchPhase.ZONE);
        boolean anyPlayed = existingMatches.stream()
                .anyMatch(m -> m.getStatus() == MatchStatus.PLAYED);
        if (anyPlayed) {
            throw new BusinessException(
                    "No se puede rehacer el fixture: ya hay partidos con resultado registrado. " +
                    "Si quedan partidos sin programar, usá \"Programar Ronda 2\".");
        }
        matchRepository.deleteAll(existingMatches);

        // 2. Generar partidos por zona
        List<Zone> zones = zoneRepository.findByTournamentIdOrderByZoneOrder(tournamentId);
        if (zones.isEmpty()) {
            throw new BusinessException("El torneo no tiene zonas generadas. Generá las zonas primero.");
        }

        List<Match> matches = new ArrayList<>();
        for (Zone zone : zones) {
            matches.addAll(generateZoneMatches(zone, tournament));
        }

        // 3. Obtener canchas activas del complejo
        List<Court> courts = courtRepository.findByComplexIdAndActiveTrue(tournament.getComplex().getId());
        if (courts.isEmpty()) {
            throw new BusinessException("El complejo no tiene canchas activas");
        }
        Map<Long, Court> courtMap = courts.stream().collect(Collectors.toMap(Court::getId, c -> c));

        // 4. Obtener disponibilidad de canchas y pulmones
        Map<Long, List<CourtAvailability>> availabilityByCourtId = courts.stream()
                .collect(Collectors.toMap(
                        Court::getId,
                        c -> courtAvailabilityRepository.findByCourtIdOrderByDayOfWeek(c.getId())
                ));
        List<TournamentBuffer> buffers = bufferRepository.findByTournamentIdOrderByDayOfWeek(tournamentId);

        // 5. Generar slots válidos
        List<TimeSlot> slots = generateTimeSlots(tournament, courts, availabilityByCourtId, buffers);
        // Si no hay ningún slot disponible, no tiene sentido seguir: avisar al usuario.
        if (slots.isEmpty()) {
            // Verificar la causa más común: canchas sin availability
            boolean noAvailability = availabilityByCourtId.values().stream().allMatch(List::isEmpty);
            String msg = noAvailability
                    ? "Las canchas del complejo \"" + tournament.getComplex().getName()
                        + "\" no tienen horarios de disponibilidad configurados. "
                        + "Configurá los horarios de las canchas antes de generar el fixture."
                    : "No hay slots disponibles para programar los partidos. "
                        + "Revisá las fechas del torneo, los días de zona y los horarios de las canchas.";
            throw new BusinessException(msg);
        }
        // Ordenar por fecha+hora antes que por cancha: así el scheduler ofrece
        // TODAS las canchas en la misma franja horaria antes de pasar a la siguiente,
        // logrando que los partidos se distribuyan entre canchas simultáneamente.
        slots.sort(Comparator
                .comparing(TimeSlot::getDate)
                .thenComparing(TimeSlot::getStartTime)
                .thenComparingLong(TimeSlot::getCourtId));
        log.info("Slots disponibles generados: {} (para {} canchas)", slots.size(), courts.size());

        // 6. Obtener todas las restricciones/preferencias de las parejas involucradas
        Map<Long, List<PairScheduleConstraint>> constraintsByPairId = buildConstraintsMap(matches);

        // 7. Programar partidos con backtracking (2 pasadas: con/sin preferencias)
        scheduleMatches(matches, slots, courtMap, tournament, constraintsByPairId, new ArrayList<>());

        // 8. Persistir
        matchRepository.saveAll(matches);

        // 9. Activar el torneo si la fecha de inicio ya llegó
        tournamentService.activateIfStarted(tournamentId);

        log.info("Fixture generado: {} partidos, {} programados, {} pendientes",
                matches.size(),
                matches.stream().filter(m -> m.getStatus() == MatchStatus.SCHEDULED).count(),
                matches.stream().filter(m -> m.getStatus() == MatchStatus.PENDING).count());

        return buildResponse(tournamentId, matches);
    }

    public FixtureResponseDto getFixture(Long tournamentId) {
        getTournamentOrThrow(tournamentId);
        List<Match> matches = matchRepository.findByTournamentIdAndPhase(tournamentId, MatchPhase.ZONE);
        return buildResponse(tournamentId, matches);
    }

    // ── Generación de partidos por zona ──────────────────────────────────────

    private List<Match> generateZoneMatches(Zone zone, Tournament tournament) {
        List<ZonePair> zonePairs = zonePairRepository.findByZoneIdOrderByPosition(zone.getId());
        List<Match> matches = new ArrayList<>();

        if (zone.getZoneSize() == 3) {
            // Todos contra todos: (1,2), (1,3), (2,3)
            matches.add(buildMatch(tournament, zone, pairAt(zonePairs, 1), pairAt(zonePairs, 2)));
            matches.add(buildMatch(tournament, zone, pairAt(zonePairs, 1), pairAt(zonePairs, 3)));
            matches.add(buildMatch(tournament, zone, pairAt(zonePairs, 2), pairAt(zonePairs, 3)));
        } else {
            // 4 parejas: 1°vs4°, 2°vs3° (ronda 1)
            Match m1 = buildMatch(tournament, zone, pairAt(zonePairs, 1), pairAt(zonePairs, 4));
            m1.setZoneRound(1);
            Match m2 = buildMatch(tournament, zone, pairAt(zonePairs, 2), pairAt(zonePairs, 3));
            m2.setZoneRound(1);
            matches.add(m1);
            matches.add(m2);

            // Ronda 2 como placeholders SIN parejas ("Ganador vs Ganador" / "Perdedor vs Perdedor"):
            // se programan desde el inicio (ambos en el MISMO horario, canchas distintas) y las
            // parejas se rellenan al cerrarse la Ronda 1 (MatchResultService.tryCreateRound2).
            Match winners = buildMatch(tournament, zone, null, null);
            winners.setZoneRound(2);
            winners.setZoneRound2Type(ZoneRound2Type.WINNERS);
            Match losers = buildMatch(tournament, zone, null, null);
            losers.setZoneRound(2);
            losers.setZoneRound2Type(ZoneRound2Type.LOSERS);
            matches.add(winners);
            matches.add(losers);
        }

        return matches;
    }

    private Match buildMatch(Tournament tournament, Zone zone, Pair pair1, Pair pair2) {
        return Match.builder()
                .tournament(tournament)
                .phase(MatchPhase.ZONE)
                .zone(zone)
                .pair1(pair1)
                .pair2(pair2)
                .status(MatchStatus.PENDING)
                .build();
    }

    private Pair pairAt(List<ZonePair> zonePairs, int position) {
        return zonePairs.stream()
                .filter(zp -> zp.getPosition() == position)
                .findFirst()
                .orElseThrow(() -> new BusinessException("No se encontró pareja en posición " + position))
                .getPair();
    }

    // ── Generación de slots horarios disponibles ──────────────────────────────

    private List<TimeSlot> generateTimeSlots(Tournament tournament,
                                              List<Court> courts,
                                              Map<Long, List<CourtAvailability>> availabilityByCourtId,
                                              List<TournamentBuffer> buffers) {
        List<TimeSlot> slots = new ArrayList<>();
        int matchDuration = tournament.getMatchDurationMinutes();

        List<Integer> zoneDays = tournament.getZoneDays(); // 1=Lun … 7=Dom; vacío = sin filtro

        LocalDate current = tournament.getStartDate();
        while (!current.isAfter(tournament.getEndDate())) {
            int dowForCourtAvail = current.getDayOfWeek().getValue() - 1; // 0=Lun … 6=Dom (convención court_availability)
            int dowForZoneDays   = current.getDayOfWeek().getValue();     // 1=Lun … 7=Dom

            // Si hay días de zona configurados, saltar los días que no correspondan
            if (!zoneDays.isEmpty() && !zoneDays.contains(dowForZoneDays)) {
                current = current.plusDays(1);
                continue;
            }

            for (Court court : courts) {
                List<CourtAvailability> courtAvailabilities = availabilityByCourtId.get(court.getId());
                Optional<CourtAvailability> avail = courtAvailabilities.stream()
                        .filter(a -> a.getDayOfWeek().equals(dowForCourtAvail))
                        .findFirst();

                if (avail.isPresent()) {
                    List<TimeSlot> daySlots = generateSlotsForDay(
                            current, avail.get(), buffers, dowForCourtAvail, court.getId(), matchDuration);
                    slots.addAll(daySlots);
                }
            }
            current = current.plusDays(1);
        }
        return slots;
    }

    private List<TimeSlot> generateSlotsForDay(LocalDate date,
                                                CourtAvailability availability,
                                                List<TournamentBuffer> buffers,
                                                int dow,
                                                Long courtId,
                                                int matchDurationMinutes) {
        List<TimeSlot> slots = new ArrayList<>();
        List<TournamentBuffer> dayBuffers = buffers.stream()
                .filter(b -> b.getDayOfWeek().equals(dow))
                .toList();

        LocalTime cursor = availability.getOpenTime();
        LocalTime closeTime = availability.getCloseTime();
        // Pulmón horario de esta cancha+día (opcional): ningún slot puede solaparlo
        LocalTime breakStart = availability.getBreakStart();
        LocalTime breakEnd = availability.getBreakEnd();
        boolean hasBreak = breakStart != null && breakEnd != null;

        while (!cursor.isAfter(closeTime.minusMinutes(matchDurationMinutes))) {
            LocalTime slotEnd = cursor.plusMinutes(matchDurationMinutes);

            boolean inBreak = hasBreak && timesOverlap(cursor, slotEnd, breakStart, breakEnd);
            if (!overlapsAnyBuffer(cursor, slotEnd, dayBuffers) && !inBreak) {
                slots.add(TimeSlot.builder()
                        .date(date)
                        .startTime(cursor)
                        .endTime(slotEnd)
                        .courtId(courtId)
                        .build());
            }
            cursor = cursor.plusMinutes(SLOT_STEP_MINUTES);
        }
        return slots;
    }

    private boolean overlapsAnyBuffer(LocalTime start, LocalTime end, List<TournamentBuffer> buffers) {
        for (TournamentBuffer buffer : buffers) {
            // Overlap: start < bufferEnd AND end > bufferStart
            if (start.isBefore(buffer.getBufferEnd()) && end.isAfter(buffer.getBufferStart())) {
                return true;
            }
        }
        return false;
    }

    // ── Backtracking / Scheduling ─────────────────────────────────────────────

    private void scheduleMatches(List<Match> matches,
                                  List<TimeSlot> slots,
                                  Map<Long, Court> courtMap,
                                  Tournament tournament,
                                  Map<Long, List<PairScheduleConstraint>> constraintsByPairId,
                                  List<Match> alreadyScheduled) {

        // Partidos ya programados/jugados actúan como restricciones de ocupación
        List<Match> scheduled = new ArrayList<>(alreadyScheduled);

        // Parejas "efectivas" de cada partido: las reales, o las 4 de la zona para los
        // placeholders de Ronda 2 (no se sabe quién juega cuál, pero las 4 juegan a esa hora).
        Map<Match, List<Long>> pairsOf = buildEffectivePairs(matches);

        // Unidades de programación: los 2 partidos de Ronda 2 de una zona de 4 van VINCULADOS
        // (mismo día y hora, canchas distintas); el resto se programa individualmente.
        List<List<Match>> units = buildUnits(matches);

        // Ordenar por "restrictedness": los con menos slots compatibles primero.
        // Así los más restringidos toman los pocos slots que pueden usar antes que partidos
        // flexibles "se coman" esos slots y los obliguen a violar preferencias.
        units.sort(Comparator.comparingInt(
                u -> countCompatibleSlots(u.get(0), slots, constraintsByPairId, pairsOf.get(u.get(0)))
        ));

        // Log del orden y restrictedness — útil para debug
        log.info("📋 Orden de procesamiento (más restringidos primero):");
        for (List<Match> u : units) {
            int compat = countCompatibleSlots(u.get(0), slots, constraintsByPairId, pairsOf.get(u.get(0)));
            log.info("   • {} → {} slots compatibles", unitLabel(u), compat);
        }

        // ── 1) BACKTRACKING: buscar una asignación COMPLETA (0 pendientes) ──
        // Las hard constraints son obligatorias; las preferencias se usan como heurística de
        // orden (se prueban primero los slots que las respetan). Tope de tiempo para no colgar.
        long deadline = System.nanoTime() + BACKTRACK_BUDGET_NANOS;
        boolean solved = backtrack(units, 0, slots, courtMap, tournament, constraintsByPairId, pairsOf, scheduled, deadline);
        if (solved) {
            log.info("Scheduling (backtracking): solución COMPLETA — 0 pendientes ({} partidos)", matches.size());
            return;
        }

        // ── 2) Sin solución completa (o se agotó el tiempo) → greedy best-effort ──
        log.info("Scheduling: el backtracking no halló solución completa; uso greedy (programa lo que pueda)");
        for (List<Match> u : units) {
            for (Match m : u) {
                m.setStatus(MatchStatus.PENDING);
                m.setScheduledStart(null);
                m.setScheduledEnd(null);
                m.setCourt(null);
            }
        }
        greedySchedule(units, slots, courtMap, tournament, constraintsByPairId, pairsOf,
                new ArrayList<>(alreadyScheduled));
    }

    /** Agrupa los partidos de Ronda 2 de cada zona de 4 como unidad vinculada (mismo horario). */
    private List<List<Match>> buildUnits(List<Match> matches) {
        Map<Long, List<Match>> round2ByZone = new LinkedHashMap<>();
        List<List<Match>> units = new ArrayList<>();
        for (Match m : matches) {
            boolean linkedRound2 = m.getPhase() == MatchPhase.ZONE
                    && Integer.valueOf(2).equals(m.getZoneRound())
                    && m.getZoneRound2Type() != null
                    && m.getZone() != null;
            if (linkedRound2) {
                round2ByZone.computeIfAbsent(m.getZone().getId(), k -> new ArrayList<>()).add(m);
            } else {
                units.add(new ArrayList<>(List.of(m)));
            }
        }
        for (List<Match> twins : round2ByZone.values()) {
            // WINNERS primero, para que el label/orden sea estable
            twins.sort(Comparator.comparing(m -> m.getZoneRound2Type().ordinal()));
            units.add(twins);
        }
        return units;
    }

    /** Parejas efectivas por partido (las 4 de la zona para placeholders de R2 sin parejas). */
    private Map<Match, List<Long>> buildEffectivePairs(List<Match> matches) {
        Map<Match, List<Long>> map = new IdentityHashMap<>();
        Map<Long, List<Long>> zoneCache = new HashMap<>();
        for (Match m : matches) {
            map.put(m, computeEffectivePairIds(m, zoneCache));
        }
        return map;
    }

    private List<Long> computeEffectivePairIds(Match m, Map<Long, List<Long>> zoneCache) {
        if (m.getPair1() != null) {
            List<Long> ids = new ArrayList<>();
            ids.add(m.getPair1().getId());
            if (m.getPair2() != null) ids.add(m.getPair2().getId());
            return ids;
        }
        if (m.getZone() != null) {
            return zoneCache.computeIfAbsent(m.getZone().getId(), zid ->
                    zonePairRepository.findByZoneIdOrderByPosition(zid).stream()
                            .map(zp -> zp.getPair().getId())
                            .toList());
        }
        return List.of();
    }

    /** Parejas efectivas de un partido suelto (operaciones manuales: mover, placements). */
    private List<Long> effectivePairIds(Match m) {
        return computeEffectivePairIds(m, new HashMap<>());
    }

    private String unitLabel(List<Match> unit) {
        if (unit.size() == 2) {
            String zone = unit.get(0).getZone() != null ? unit.get(0).getZone().getName() : "?";
            return "Zona " + zone + " R2 (Gan+Perd, simultáneos)";
        }
        Match m = unit.get(0);
        String p1 = m.getPair1() != null ? "pareja " + m.getPair1().getId()
                : (m.getZoneRound2Type() != null ? m.getZoneRound2Type().name() : "?");
        String p2 = m.getPair2() != null ? "pareja " + m.getPair2().getId() : "—";
        return p1 + " vs " + p2;
    }

    /** Presupuesto de tiempo del backtracking antes de caer al greedy. */
    private static final long BACKTRACK_BUDGET_NANOS = 3_000_000_000L; // 3 segundos

    /**
     * Backtracking con la ordenación MRV ya aplicada (más restringidos primero) y forward-checking.
     * Opera sobre UNIDADES: un partido suelto, o los 2 de Ronda 2 de una zona de 4 (vinculados:
     * mismo día y hora, canchas distintas). Solo hard constraints son obligatorias; los slots que
     * respetan preferencias se prueban primero. Devuelve true si logró asignación completa.
     */
    private boolean backtrack(List<List<Match>> units, int idx, List<TimeSlot> slots,
                              Map<Long, Court> courtMap, Tournament tournament,
                              Map<Long, List<PairScheduleConstraint>> cons,
                              Map<Match, List<Long>> pairsOf,
                              List<Match> scheduled, long deadline) {
        if (idx == units.size()) return true;
        if (System.nanoTime() > deadline) return false;

        List<Match> unit = units.get(idx);
        Match rep = unit.get(0);
        List<Long> repPairs = pairsOf.get(rep);
        boolean hasPrefs = hasAnyPreference(rep, cons, repPairs);

        // Candidatos válidos por hard constraints; los que cumplen preferencias van primero
        List<TimeSlot> preferred = new ArrayList<>();
        List<TimeSlot> others = new ArrayList<>();
        for (TimeSlot slot : slots) {
            if (!isValidSlot(rep, slot, scheduled, tournament, cons, false, repPairs)) continue;
            if (hasPrefs && isValidSlot(rep, slot, scheduled, tournament, cons, true, repPairs)) preferred.add(slot);
            else others.add(slot);
        }

        for (List<TimeSlot> bucket : List.of(preferred, others)) {
            for (TimeSlot slot : bucket) {
                List<Match> placed = placeUnit(unit, slot, slots, courtMap, tournament, cons, pairsOf, scheduled);
                if (placed == null) continue; // (twin sin segunda cancha libre en ese horario)

                if (backtrack(units, idx + 1, slots, courtMap, tournament, cons, pairsOf, scheduled, deadline)) {
                    return true;
                }

                // revertir
                for (Match m : placed) {
                    scheduled.remove(m);
                    m.setScheduledStart(null);
                    m.setScheduledEnd(null);
                    m.setCourt(null);
                    m.setStatus(MatchStatus.PENDING);
                }

                if (System.nanoTime() > deadline) return false;
            }
        }
        return false;
    }

    /**
     * Coloca la unidad tomando `slot` para el primer partido. Si la unidad es un twin de Ronda 2,
     * busca una SEGUNDA cancha libre en el mismo día y hora para el otro partido; si no hay,
     * devuelve null sin tocar nada. Devuelve los partidos colocados (para poder revertir).
     */
    private List<Match> placeUnit(List<Match> unit, TimeSlot slot, List<TimeSlot> allSlots,
                                  Map<Long, Court> courtMap, Tournament tournament,
                                  Map<Long, List<PairScheduleConstraint>> cons,
                                  Map<Match, List<Long>> pairsOf, List<Match> scheduled) {
        Match first = unit.get(0);
        assignSlot(first, slot, courtMap);
        scheduled.add(first);
        if (unit.size() == 1) return List.of(first);

        // Twin: mismo día+hora, otra cancha. Se valida contra `scheduled` SIN el twin recién
        // colocado (comparten parejas efectivas: chocarían por concurrencia/intervalo), pero
        // la ocupación de SU cancha se excluye exigiendo cancha distinta.
        Match second = unit.get(1);
        List<Match> withoutFirst = new ArrayList<>(scheduled);
        withoutFirst.remove(first);
        for (TimeSlot s2 : allSlots) {
            if (!s2.getDate().equals(slot.getDate())) continue;
            if (!s2.getStartTime().equals(slot.getStartTime())) continue;
            if (s2.getCourtId().equals(slot.getCourtId())) continue;
            if (!isValidSlot(second, s2, withoutFirst, tournament, cons, false, pairsOf.get(second))) continue;
            assignSlot(second, s2, courtMap);
            scheduled.add(second);
            return List.of(first, second);
        }
        // sin segunda cancha: revertir el primero
        scheduled.remove(first);
        first.setScheduledStart(null);
        first.setScheduledEnd(null);
        first.setCourt(null);
        first.setStatus(MatchStatus.PENDING);
        return null;
    }

    private void assignSlot(Match match, TimeSlot slot, Map<Long, Court> courtMap) {
        match.setScheduledStart(LocalDateTime.of(slot.getDate(), slot.getStartTime()));
        match.setScheduledEnd(LocalDateTime.of(slot.getDate(), slot.getEndTime()));
        match.setCourt(courtMap.get(slot.getCourtId()));
        match.setStatus(MatchStatus.SCHEDULED);
    }

    /** Greedy best-effort (fallback): a cada unidad le asigna el primer slot válido, sin deshacer. */
    private void greedySchedule(List<List<Match>> units, List<TimeSlot> slots, Map<Long, Court> courtMap,
                                Tournament tournament, Map<Long, List<PairScheduleConstraint>> constraintsByPairId,
                                Map<Match, List<Long>> pairsOf, List<Match> scheduled) {
        int satisfiedWithPrefs = 0;
        int relaxedFromPrefs = 0;
        int unscheduled = 0;

        for (List<Match> unit : units) {
            Match rep = unit.get(0);
            List<Long> repPairs = pairsOf.get(rep);
            boolean matchHasPrefs = hasAnyPreference(rep, constraintsByPairId, repPairs);
            List<Match> placed = null;
            boolean fromPreferences = false;

            // Pasada 1: intenta respetar preferencias (soft constraints ON)
            for (TimeSlot slot : slots) {
                if (!isValidSlot(rep, slot, scheduled, tournament, constraintsByPairId, true, repPairs)) continue;
                placed = placeUnit(unit, slot, slots, courtMap, tournament, constraintsByPairId, pairsOf, scheduled);
                if (placed != null) { fromPreferences = true; break; }
            }

            // Pasada 2: si no encontró, relaja preferencias (solo hard constraints)
            if (placed == null) {
                if (matchHasPrefs) {
                    log.warn("   ⚠ {} no encontró slot en preferencias, relajando...", unitLabel(unit));
                }
                for (TimeSlot slot : slots) {
                    if (!isValidSlot(rep, slot, scheduled, tournament, constraintsByPairId, false, repPairs)) continue;
                    placed = placeUnit(unit, slot, slots, courtMap, tournament, constraintsByPairId, pairsOf, scheduled);
                    if (placed != null) break;
                }
            }

            if (placed != null) {
                if (fromPreferences) {
                    satisfiedWithPrefs += unit.size();
                } else if (matchHasPrefs) {
                    relaxedFromPrefs += unit.size();
                    log.warn("⚠ Preferencia NO respetada — {} → programado {} {} (fuera de las preferencias de las parejas)",
                            unitLabel(unit), rep.getScheduledStart().toLocalDate(), rep.getScheduledStart().toLocalTime());
                } else {
                    satisfiedWithPrefs += unit.size(); // sin preferencias = no se violó nada
                }
            } else {
                unscheduled += unit.size();
                log.warn("No se pudo programar: {}", unitLabel(unit));
            }
        }

        log.info("Scheduling (greedy): {} respetando preferencias · {} con preferencia relajada · {} pendientes",
                satisfiedWithPrefs, relaxedFromPrefs, unscheduled);
    }

    /**
     * Cuenta cuántos slots del calendario son compatibles con las constraints
     * (restricciones + preferencias) de las parejas del match. Ignora ocupación
     * de otros partidos — solo mira las constraints del match en sí.
     *
     * Sirve como heurística de "restrictedness": menor número = partido más restringido =
     * debe procesarse primero.
     */
    private int countCompatibleSlots(Match match, List<TimeSlot> allSlots,
                                      Map<Long, List<PairScheduleConstraint>> constraintsByPairId,
                                      List<Long> pairIds) {
        if (match.getPhase() != MatchPhase.ZONE) return allSlots.size();

        int count = 0;
        for (TimeSlot slot : allSlots) {
            boolean ok = true;
            for (Long pid : pairIds) {
                List<PairScheduleConstraint> cs = constraintsByPairId.getOrDefault(pid, List.of());
                if (violatesRestriction(slot, cs)
                        || (hasPreferences(cs) && !satisfiesPreference(slot, cs))) {
                    ok = false;
                    break;
                }
            }
            if (ok) count++;
        }
        return count;
    }

    /** True si alguna pareja (efectiva) del match tiene al menos una preferencia. */
    private boolean hasAnyPreference(Match match,
                                      Map<Long, List<PairScheduleConstraint>> constraintsByPairId,
                                      List<Long> pairIds) {
        if (match.getPhase() != MatchPhase.ZONE) return false;
        for (Long pid : pairIds) {
            if (hasPreferences(constraintsByPairId.getOrDefault(pid, List.of()))) return true;
        }
        return false;
    }

    private boolean isValidSlot(Match match,
                                  TimeSlot slot,
                                  List<Match> scheduled,
                                  Tournament tournament,
                                  Map<Long, List<PairScheduleConstraint>> constraintsByPairId,
                                  boolean enforcePreferences,
                                  List<Long> pairIds) {

        int minInterval = tournament.getMinIntervalMinutes();

        // 1. Cancha no ocupada en ese slot
        if (isCourtOccupied(slot, scheduled)) return false;

        // 2 y 3. Por cada pareja efectiva (las 4 de la zona en placeholders de R2):
        // sin otro partido en ese slot, y respetando el intervalo mínimo inicio-a-inicio.
        for (Long pid : pairIds) {
            if (isPairBusy(pid, slot, scheduled)) return false;
            if (violatesMinInterval(pid, slot, scheduled, minInterval)) return false;
        }

        // 3b. Orden de rondas en zona de 4: la Ronda 2 (gan/per) debe ir DESPUÉS de la
        // Ronda 1 de sus parejas (comparando fecha y hora completas).
        if (violatesZoneRoundOrder(match, slot, scheduled, pairIds)) return false;

        // 4. Restricciones y preferencias: solo aplican a partidos de zona
        if (match.getPhase() == MatchPhase.ZONE) {
            for (Long pid : pairIds) {
                List<PairScheduleConstraint> cs = constraintsByPairId.getOrDefault(pid, List.of());
                if (violatesRestriction(slot, cs)) return false;
                if (enforcePreferences && hasPreferences(cs) && !satisfiesPreference(slot, cs)) return false;
            }
        }

        return true;
    }

    private boolean isCourtOccupied(TimeSlot slot, List<Match> scheduled) {
        return scheduled.stream()
                .filter(m -> m.getCourt() != null
                        && m.getCourt().getId().equals(slot.getCourtId())
                        && m.getScheduledStart().toLocalDate().equals(slot.getDate()))
                .anyMatch(m -> timesOverlap(
                        m.getScheduledStart().toLocalTime(), m.getScheduledEnd().toLocalTime(),
                        slot.getStartTime(), slot.getEndTime()));
    }

    private boolean isPairBusy(Long pairId, TimeSlot slot, List<Match> scheduled) {
        return scheduled.stream()
                .filter(m -> involvesPair(m, pairId)
                        && m.getScheduledStart().toLocalDate().equals(slot.getDate()))
                .anyMatch(m -> timesOverlap(
                        m.getScheduledStart().toLocalTime(), m.getScheduledEnd().toLocalTime(),
                        slot.getStartTime(), slot.getEndTime()));
    }

    private boolean violatesMinInterval(Long pairId, TimeSlot slot, List<Match> scheduled, int minIntervalMinutes) {
        LocalDate slotDate = slot.getDate();
        LocalTime slotStart = slot.getStartTime();

        return scheduled.stream()
                .filter(m -> involvesPair(m, pairId)
                        && m.getScheduledStart().toLocalDate().equals(slotDate))
                .anyMatch(m -> {
                    LocalTime prevStart = m.getScheduledStart().toLocalTime();
                    // Intervalo mínimo medido de INICIO a INICIO entre partidos de la misma pareja.
                    // Ej: intervalo 120 → si juega 10:00, el siguiente no puede empezar antes de 12:00.
                    long diffMinutes = Math.abs(slotStart.toSecondOfDay() - prevStart.toSecondOfDay()) / 60L;
                    return diffMinutes < minIntervalMinutes;
                });
    }

    /**
     * En zonas de 4, la Ronda 2 (ganadores / perdedores) debe jugarse DESPUÉS de que termine la
     * Ronda 1 de sus parejas (orden correcto, sin solape; vale también entre días). El intervalo
     * mínimo inicio-a-inicio entre partidos de la pareja lo garantiza violatesMinInterval.
     */
    private boolean violatesZoneRoundOrder(Match match, TimeSlot slot, List<Match> scheduled, List<Long> pairIds) {
        Integer round = match.getZoneRound();
        if (match.getPhase() != MatchPhase.ZONE || round == null) return false;

        LocalDateTime slotStart = LocalDateTime.of(slot.getDate(), slot.getStartTime());

        return scheduled.stream()
                .filter(m -> m.getZoneRound() != null && m.getZoneRound() < round) // rondas previas de la zona
                .filter(m -> m.getScheduledEnd() != null)
                .filter(m -> pairIds.stream().anyMatch(pid -> involvesPair(m, pid)))
                .anyMatch(m -> slotStart.isBefore(m.getScheduledEnd()));
    }

    private boolean violatesRestriction(TimeSlot slot, List<PairScheduleConstraint> constraints) {
        int dow = slot.getDate().getDayOfWeek().getValue(); // 1=Lun … 7=Dom (igual que el frontend)
        return constraints.stream()
                .filter(c -> c.getConstraintType() == ConstraintType.RESTRICTION)
                .filter(c -> c.getDayOfWeek().equals(dow))
                .anyMatch(c -> timesOverlap(c.getSlotStart(), c.getSlotEnd(),
                        slot.getStartTime(), slot.getEndTime()));
    }

    private boolean hasPreferences(List<PairScheduleConstraint> constraints) {
        return constraints.stream().anyMatch(c -> c.getConstraintType() == ConstraintType.PREFERENCE);
    }

    private boolean satisfiesPreference(TimeSlot slot, List<PairScheduleConstraint> constraints) {
        int dow = slot.getDate().getDayOfWeek().getValue(); // 1=Lun … 7=Dom (igual que el frontend)
        // El slot debe estar COMPLETAMENTE contenido dentro de la franja de preferencia.
        // (Si solo overlapea parcialmente, parte del partido cae fuera del horario preferido,
        // y eso NO es lo que esperan los jugadores.)
        return constraints.stream()
                .filter(c -> c.getConstraintType() == ConstraintType.PREFERENCE)
                .filter(c -> c.getDayOfWeek().equals(dow))
                .anyMatch(c ->
                        !slot.getStartTime().isBefore(c.getSlotStart()) &&  // slot.start >= pref.start
                        !slot.getEndTime().isAfter(c.getSlotEnd())          // slot.end   <= pref.end
                );
    }

    private boolean timesOverlap(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        return start1.isBefore(end2) && end1.isAfter(start2);
    }

    private boolean involvesPair(Match m, Long pairId) {
        return (m.getPair1() != null && m.getPair1().getId().equals(pairId))
                || (m.getPair2() != null && m.getPair2().getId().equals(pairId));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<Long, List<PairScheduleConstraint>> buildConstraintsMap(List<Match> matches) {
        Map<Long, List<PairScheduleConstraint>> map = new HashMap<>();
        Set<Long> pairIds = new HashSet<>();
        Map<Long, List<Long>> zoneCache = new HashMap<>();
        for (Match m : matches) {
            // Incluye las parejas efectivas: en placeholders de R2 (sin parejas), las 4 de la zona
            pairIds.addAll(computeEffectivePairIds(m, zoneCache));
        }
        for (Long pairId : pairIds) {
            map.put(pairId, constraintRepository.findByPairId(pairId));
        }
        return map;
    }

    // ── Programar solo partidos pendientes (se usa después de registrar resultados) ──

    @Transactional
    public FixtureResponseDto schedulePendingMatches(Long tournamentId) {
        Tournament tournament = getTournamentOrThrow(tournamentId);

        List<Match> allMatches = matchRepository.findByTournamentIdAndPhase(tournamentId, MatchPhase.ZONE);
        List<Match> pendingMatches = allMatches.stream()
                .filter(m -> m.getStatus() == MatchStatus.PENDING)
                .collect(Collectors.toList());

        if (pendingMatches.isEmpty()) {
            log.info("No hay partidos pendientes para programar en torneo {}", tournamentId);
            return buildResponse(tournamentId, allMatches);
        }

        List<Court> courts = courtRepository.findByComplexIdAndActiveTrue(tournament.getComplex().getId());
        if (courts.isEmpty()) {
            throw new BusinessException("El complejo no tiene canchas activas");
        }
        Map<Long, Court> courtMap = courts.stream().collect(Collectors.toMap(Court::getId, c -> c));

        Map<Long, List<CourtAvailability>> availabilityByCourtId = courts.stream()
                .collect(Collectors.toMap(
                        Court::getId,
                        c -> courtAvailabilityRepository.findByCourtIdOrderByDayOfWeek(c.getId())
                ));
        List<TournamentBuffer> buffers = bufferRepository.findByTournamentIdOrderByDayOfWeek(tournamentId);
        List<TimeSlot> slots = generateTimeSlots(tournament, courts, availabilityByCourtId, buffers);
        if (slots.isEmpty()) {
            boolean noAvailability = availabilityByCourtId.values().stream().allMatch(List::isEmpty);
            String msg = noAvailability
                    ? "Las canchas del complejo \"" + tournament.getComplex().getName()
                        + "\" no tienen horarios de disponibilidad configurados."
                    : "No hay slots disponibles para programar los partidos pendientes.";
            throw new BusinessException(msg);
        }
        slots.sort(Comparator
                .comparing(TimeSlot::getDate)
                .thenComparing(TimeSlot::getStartTime)
                .thenComparingLong(TimeSlot::getCourtId));

        // Los partidos ya programados/jugados cuentan como ocupados
        List<Match> alreadyScheduled = allMatches.stream()
                .filter(m -> m.getStatus() == MatchStatus.SCHEDULED || m.getStatus() == MatchStatus.PLAYED)
                .collect(Collectors.toList());

        Map<Long, List<PairScheduleConstraint>> constraintsByPairId = buildConstraintsMap(pendingMatches);

        scheduleMatches(pendingMatches, slots, courtMap, tournament, constraintsByPairId, alreadyScheduled);

        matchRepository.saveAll(pendingMatches);

        log.info("Partidos pendientes programados: {} de {} en torneo {}",
                pendingMatches.stream().filter(m -> m.getStatus() == MatchStatus.SCHEDULED).count(),
                pendingMatches.size(), tournamentId);

        return buildResponse(tournamentId, matchRepository.findByTournamentIdAndPhase(tournamentId, MatchPhase.ZONE));
    }

    // ── Reordenar zonas para programar todos los partidos ─────────────────────

    /**
     * Cuando quedan partidos sin programar (por restricciones que se pisan, o por falta de
     * canchas), intenta intercambiar parejas entre zonas para que entren todos.
     *
     * Reglas (definidas con el usuario):
     *  - Las CABEZAS de zona (posición 1) nunca se mueven.
     *  - Se prueban intercambios DE A PARES entre zonas, cada uno INDEPENDIENTE desde la config base.
     *  - Escalado por nivel desde el fondo: primero las últimas de cada zona, luego anteúltimas, etc.
     *  - Si algún intercambio llega a 0 sin programar → se aplica. Si no, se aplica el MEJOR (menos pendientes).
     *  - Si aún quedan pendientes → se sugiere sumar canchas/otro complejo.
     */
    @Transactional
    public ReorganizeResultDto reorganizeZonesToSchedule(Long tournamentId) {
        Tournament tournament = getTournamentOrThrow(tournamentId);
        if (tournament.getComplex() == null) {
            throw new BusinessException("El torneo no tiene un complejo asignado");
        }

        List<Match> existingZone = matchRepository.findByTournamentIdAndPhase(tournamentId, MatchPhase.ZONE);
        if (existingZone.stream().anyMatch(m -> m.getStatus() == MatchStatus.PLAYED)) {
            throw new BusinessException("No se puede reordenar las zonas: ya hay partidos con resultado registrado.");
        }

        List<Zone> zones = zoneRepository.findByTournamentIdOrderByZoneOrder(tournamentId);
        if (zones.isEmpty()) {
            throw new BusinessException("El torneo no tiene zonas generadas.");
        }

        // Contexto de scheduling (una sola vez)
        List<Court> courts = courtRepository.findByComplexIdAndActiveTrue(tournament.getComplex().getId());
        if (courts.isEmpty()) {
            throw new BusinessException("El complejo no tiene canchas activas");
        }
        Map<Long, Court> courtMap = courts.stream().collect(Collectors.toMap(Court::getId, c -> c));
        Map<Long, List<CourtAvailability>> availByCourt = courts.stream().collect(Collectors.toMap(
                Court::getId, c -> courtAvailabilityRepository.findByCourtIdOrderByDayOfWeek(c.getId())));
        List<TournamentBuffer> buffers = bufferRepository.findByTournamentIdOrderByDayOfWeek(tournamentId);
        List<TimeSlot> slots = generateTimeSlots(tournament, courts, availByCourt, buffers);
        if (slots.isEmpty()) {
            throw new BusinessException("No hay slots disponibles (revisá horarios de canchas).");
        }
        slots.sort(Comparator.comparing(TimeSlot::getDate)
                .thenComparing(TimeSlot::getStartTime)
                .thenComparingLong(TimeSlot::getCourtId));

        // Arreglo base: por zona, lista de parejas ordenada por posición (índice 0 = cabeza)
        Map<Long, Zone> zoneById = new LinkedHashMap<>();
        Map<Long, List<Pair>> base = new LinkedHashMap<>();
        for (Zone z : zones) {
            zoneById.put(z.getId(), z);
            List<Pair> ordered = zonePairRepository.findByZoneIdOrderByPosition(z.getId())
                    .stream().map(ZonePair::getPair).collect(Collectors.toList());
            if (ordered.size() != z.getZoneSize()) {
                throw new BusinessException("La Zona " + z.getName() + " está incompleta. Regenerá las zonas.");
            }
            base.put(z.getId(), ordered);
        }

        // Restricciones de todas las parejas (una vez)
        Map<Long, List<PairScheduleConstraint>> constraints = new HashMap<>();
        for (List<Pair> ps : base.values()) {
            for (Pair p : ps) constraints.computeIfAbsent(p.getId(), constraintRepository::findByPairId);
        }

        int basePending = evaluateArrangement(base, zones, tournament, slots, courtMap, constraints);
        Map<Long, List<Pair>> best = deepCopyArrangement(base);
        int bestPending = basePending;
        String swapDesc = null;

        if (basePending > 0) {
            outer:
            for (int d = 0; ; d++) {
                // Candidatos del nivel d: par en posición (size - d) de cada zona, si esa posición >= 2 (no cabeza)
                List<long[]> candidates = new ArrayList<>(); // [zoneId, índice 0-based]
                for (Zone z : zones) {
                    int pos = z.getZoneSize() - d; // 1-based
                    if (pos >= 2) candidates.add(new long[]{ z.getId(), pos - 1 });
                }
                if (candidates.size() < 2) break; // sin más niveles con al menos 2 zonas intercambiables

                for (int i = 0; i < candidates.size(); i++) {
                    for (int j = i + 1; j < candidates.size(); j++) {
                        Map<Long, List<Pair>> trial = deepCopyArrangement(base); // independiente desde base
                        long ziA = candidates.get(i)[0]; int idxA = (int) candidates.get(i)[1];
                        long ziB = candidates.get(j)[0]; int idxB = (int) candidates.get(j)[1];
                        Pair pa = trial.get(ziA).get(idxA);
                        Pair pb = trial.get(ziB).get(idxB);
                        trial.get(ziA).set(idxA, pb);
                        trial.get(ziB).set(idxB, pa);

                        int pending = evaluateArrangement(trial, zones, tournament, slots, courtMap, constraints);
                        if (pending < bestPending) {
                            bestPending = pending;
                            best = deepCopyArrangement(trial);
                            swapDesc = "Zona " + zoneById.get(ziA).getName() + " ↔ Zona " + zoneById.get(ziB).getName();
                        }
                        if (pending == 0) break outer;
                    }
                }
            }
        }

        boolean changed = !sameArrangement(base, best);
        if (changed) {
            persistArrangement(best, zoneById);
        }

        // Regenerar el fixture persistido con el mejor arreglo (reutiliza la generación estándar)
        FixtureResponseDto fixture = generateFixture(tournamentId);

        String message;
        if (bestPending == 0) {
            message = changed
                    ? "Listo: se programaron todos los partidos reordenando zonas (" + swapDesc + ")."
                    : "Ya estaban todos los partidos programados.";
        } else {
            message = "Aún quedan " + bestPending + " partido(s) sin programar después de probar todos los "
                    + "intercambios posibles. Conviene sumar otra cancha o complejo.";
        }
        log.info("Reordenar zonas torneo {}: basePending={} → bestPending={} (cambió={})",
                tournamentId, basePending, bestPending, changed);

        return ReorganizeResultDto.builder()
                .solved(bestPending == 0)
                .pending(bestPending)
                .swapApplied(changed ? swapDesc : null)
                .suggestMoreCourts(bestPending > 0)
                .message(message)
                .fixture(fixture)
                .build();
    }

    /** Cuenta partidos sin programar (status != SCHEDULED) para un arreglo dado, sin persistir. */
    private int evaluateArrangement(Map<Long, List<Pair>> arrangement, List<Zone> zones, Tournament t,
                                    List<TimeSlot> slots, Map<Long, Court> courtMap,
                                    Map<Long, List<PairScheduleConstraint>> constraints) {
        List<Match> matches = new ArrayList<>();
        for (Zone z : zones) {
            matches.addAll(buildZoneMatchesFromPairs(z, arrangement.get(z.getId()), t));
        }
        scheduleMatches(matches, slots, courtMap, t, constraints, new ArrayList<>());
        return (int) matches.stream().filter(m -> m.getStatus() != MatchStatus.SCHEDULED).count();
    }

    /** Genera los partidos de una zona a partir de una lista de parejas en memoria (índice 0 = posición 1). */
    private List<Match> buildZoneMatchesFromPairs(Zone zone, List<Pair> pairs, Tournament t) {
        List<Match> matches = new ArrayList<>();
        if (zone.getZoneSize() == 3) {
            matches.add(buildMatch(t, zone, pairs.get(0), pairs.get(1)));
            matches.add(buildMatch(t, zone, pairs.get(0), pairs.get(2)));
            matches.add(buildMatch(t, zone, pairs.get(1), pairs.get(2)));
        } else {
            Match m1 = buildMatch(t, zone, pairs.get(0), pairs.get(3)); m1.setZoneRound(1);
            Match m2 = buildMatch(t, zone, pairs.get(1), pairs.get(2)); m2.setZoneRound(1);
            matches.add(m1);
            matches.add(m2);
        }
        return matches;
    }

    private Map<Long, List<Pair>> deepCopyArrangement(Map<Long, List<Pair>> src) {
        Map<Long, List<Pair>> out = new LinkedHashMap<>();
        src.forEach((k, v) -> out.put(k, new ArrayList<>(v)));
        return out;
    }

    private boolean sameArrangement(Map<Long, List<Pair>> a, Map<Long, List<Pair>> b) {
        for (Long zid : a.keySet()) {
            List<Pair> la = a.get(zid), lb = b.get(zid);
            if (la.size() != lb.size()) return false;
            for (int i = 0; i < la.size(); i++) {
                if (!la.get(i).getId().equals(lb.get(i).getId())) return false;
            }
        }
        return true;
    }

    /** Reemplaza los zone_pairs por el arreglo dado (borra y recrea por el unique (zone_id, position)). */
    private void persistArrangement(Map<Long, List<Pair>> arrangement, Map<Long, Zone> zoneById) {
        for (Long zid : arrangement.keySet()) {
            zonePairRepository.deleteAll(zonePairRepository.findByZoneIdOrderByPosition(zid));
        }
        zonePairRepository.flush();
        for (Map.Entry<Long, List<Pair>> e : arrangement.entrySet()) {
            Zone zone = zoneById.get(e.getKey());
            List<Pair> pairs = e.getValue();
            for (int i = 0; i < pairs.size(); i++) {
                zonePairRepository.save(ZonePair.builder()
                        .zone(zone).pair(pairs.get(i)).position(i + 1).build());
            }
        }
        zonePairRepository.flush();
    }

    private Tournament getTournamentOrThrow(Long id) {
        return tournamentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", id));
    }

    // ── Mapeo a DTO ───────────────────────────────────────────────────────────

    private FixtureResponseDto buildResponse(Long tournamentId, List<Match> matches) {
        List<MatchResponseDto> matchDtos = matches.stream()
                .map(this::toMatchDto)
                .toList();

        // "Programados" = tienen slot asignado (SCHEDULED, CONFIRMED o ya PLAYED)
        // "Pendientes"  = sin slot aún (PENDING) — solo estos necesitan ser programados
        long scheduledCount = matches.stream()
                .filter(m -> m.getStatus() == MatchStatus.SCHEDULED
                          || m.getStatus() == MatchStatus.CONFIRMED
                          || m.getStatus() == MatchStatus.PLAYED)
                .count();
        long pendingCount = matches.stream()
                .filter(m -> m.getStatus() == MatchStatus.PENDING)
                .count();

        return FixtureResponseDto.builder()
                .tournamentId(tournamentId)
                .totalMatches(matches.size())
                .scheduledCount((int) scheduledCount)
                .pendingCount((int) pendingCount)
                .matches(matchDtos)
                .build();
    }

    private MatchResponseDto toMatchDto(Match match) {
        MatchResponseDto.MatchResponseDtoBuilder builder = MatchResponseDto.builder()
                .id(match.getId())
                .zoneName(match.getZone() != null ? "Zona " + match.getZone().getName() : null)
                .eliminationRound(match.getEliminationRound())
                .zoneRound(match.getZoneRound())
                .zoneRound2Type(match.getZoneRound2Type() != null ? match.getZoneRound2Type().name() : null)
                .pair1(match.getPair1() != null ? toPairDto(match.getPair1()) : null)
                .pair2(match.getPair2() != null ? toPairDto(match.getPair2()) : null)
                .courtId(match.getCourt() != null ? match.getCourt().getId() : null)
                .courtName(match.getCourt() != null ? match.getCourt().getName() : null)
                .complexName(match.getCourt() != null && match.getCourt().getComplex() != null
                        ? match.getCourt().getComplex().getName() : null)
                .scheduledStart(match.getScheduledStart())
                .scheduledEnd(match.getScheduledEnd())
                .status(match.getStatus());

        // Incluir resultado si el partido ya fue jugado
        if (match.getStatus() == MatchStatus.PLAYED) {
            matchResultRepository.findByMatchId(match.getId()).ifPresent(result -> {
                builder.winnerPairId(result.getWinnerPair().getId());
                builder.sets(result.getSets().stream()
                        .map(s -> MatchResponseDto.SetScoreDto.builder()
                                .pair1Games(s.getPair1Games())
                                .pair2Games(s.getPair2Games())
                                .build())
                        .toList());
            });
        }

        return builder.build();
    }

    // ── Actualizar cancha de un partido ───────────────────────────────────────

    @Transactional
    public MatchResponseDto updateMatchCourt(Long matchId, Long courtId, LocalDateTime scheduledStart) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido", matchId));

        if (courtId == null) {
            // Quitar cancha y horario → vuelve a PENDING
            match.setCourt(null);
            match.setScheduledStart(null);
            match.setScheduledEnd(null);
            match.setStatus(MatchStatus.PENDING);
        } else {
            Court court = courtRepository.findById(courtId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cancha", courtId));
            match.setCourt(court);

            // Si se provee un nuevo horario, actualizarlo (calculando scheduledEnd automáticamente)
            if (scheduledStart != null) {
                int duration = match.getTournament().getMatchDurationMinutes();
                match.setScheduledStart(scheduledStart);
                match.setScheduledEnd(scheduledStart.plusMinutes(duration));
            }

            // Determinar estado según disponibilidad de información.
            // OJO: no degradar un partido ya JUGADO — reprogramarlo solo cambia cancha/hora,
            // su resultado se conserva y sigue PLAYED.
            if (match.getScheduledStart() != null
                    && match.getStatus() != MatchStatus.PLAYED
                    && match.getStatus() != MatchStatus.CANCELLED) {
                match.setStatus(MatchStatus.SCHEDULED);
            }
        }

        matchRepository.save(match);
        log.info("Cancha/horario actualizado: Match {} → Court {}, Start {}",
                matchId, courtId, scheduledStart);
        return toMatchDto(match);
    }

    // ── Mover partido: destinos válidos + persistencia validada ────────────────

    /**
     * Devuelve todos los destinos posibles (cancha + fecha + hora) para mover un partido, marcando
     * cada uno como válido (verde) o inválido (rojo) según las mismas reglas del scheduler
     * (cancha/pareja libres, intervalo, orden de rondas, horario/pulmón y restricciones horarias).
     */
    @Transactional(readOnly = true)
    public List<MatchPlacementDto> getPlacementsForMatch(Long matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido", matchId));
        Tournament tournament = match.getTournament();
        if (tournament.getComplex() == null) {
            throw new BusinessException("El torneo no tiene un complejo asignado");
        }

        List<Court> courts = courtRepository.findByComplexIdAndActiveTrue(tournament.getComplex().getId());
        if (courts.isEmpty()) throw new BusinessException("El complejo no tiene canchas activas");
        Map<Long, Court> courtMap = courts.stream().collect(Collectors.toMap(Court::getId, c -> c));
        Map<Long, List<CourtAvailability>> availByCourt = courts.stream().collect(Collectors.toMap(
                Court::getId, c -> courtAvailabilityRepository.findByCourtIdOrderByDayOfWeek(c.getId())));
        List<TournamentBuffer> buffers = bufferRepository.findByTournamentIdOrderByDayOfWeek(tournament.getId());

        List<TimeSlot> slots = generateTimeSlots(tournament, courts, availByCourt, buffers);
        slots.sort(Comparator.comparing(TimeSlot::getDate)
                .thenComparing(TimeSlot::getStartTime)
                .thenComparingLong(TimeSlot::getCourtId));

        List<Match> otherScheduled = otherScheduledMatches(tournament.getId(), matchId);
        Map<Long, List<PairScheduleConstraint>> constraints = buildConstraintsMap(List.of(match));

        LocalDate curDate = match.getScheduledStart() != null ? match.getScheduledStart().toLocalDate() : null;
        LocalTime curStart = match.getScheduledStart() != null ? match.getScheduledStart().toLocalTime() : null;
        Long curCourt = match.getCourt() != null ? match.getCourt().getId() : null;

        List<MatchPlacementDto> out = new ArrayList<>();
        for (TimeSlot slot : slots) {
            boolean isCurrent = curCourt != null && curCourt.equals(slot.getCourtId())
                    && slot.getDate().equals(curDate) && slot.getStartTime().equals(curStart);
            String reason = isCurrent ? null : placementReason(match, slot, otherScheduled, tournament, constraints);
            out.add(MatchPlacementDto.builder()
                    .courtId(slot.getCourtId())
                    .courtName(courtMap.get(slot.getCourtId()).getName())
                    .date(slot.getDate())
                    .startTime(slot.getStartTime())
                    .endTime(slot.getEndTime())
                    .valid(reason == null)
                    .current(isCurrent)
                    .reason(reason)
                    .build());
        }
        return out;
    }

    /** Mueve un partido a (cancha, horario) validando el destino con las reglas del scheduler. */
    @Transactional
    public MatchResponseDto moveMatch(Long matchId, Long courtId, LocalDateTime scheduledStart) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido", matchId));
        if (courtId == null || scheduledStart == null) {
            throw new BusinessException("Para mover un partido hay que indicar cancha y horario");
        }
        Tournament tournament = match.getTournament();
        int duration = tournament.getMatchDurationMinutes();

        TimeSlot slot = TimeSlot.builder()
                .date(scheduledStart.toLocalDate())
                .startTime(scheduledStart.toLocalTime())
                .endTime(scheduledStart.toLocalTime().plusMinutes(duration))
                .courtId(courtId)
                .build();

        List<Match> otherScheduled = otherScheduledMatches(tournament.getId(), matchId);
        Map<Long, List<PairScheduleConstraint>> constraints = buildConstraintsMap(List.of(match));
        String reason = placementReason(match, slot, otherScheduled, tournament, constraints);
        if (reason != null) {
            throw new BusinessException("No se puede mover ahí: " + reason);
        }
        return updateMatchCourt(matchId, courtId, scheduledStart);
    }

    /** Partidos del torneo que ocupan cancha/horario (programados o jugados), excluyendo uno. */
    private List<Match> otherScheduledMatches(Long tournamentId, Long excludeMatchId) {
        List<Match> all = new ArrayList<>();
        all.addAll(matchRepository.findByTournamentIdAndPhase(tournamentId, MatchPhase.ZONE));
        all.addAll(matchRepository.findByTournamentIdAndPhase(tournamentId, MatchPhase.ELIMINATION));
        return all.stream()
                .filter(m -> !m.getId().equals(excludeMatchId))
                .filter(m -> m.getScheduledStart() != null && m.getCourt() != null)
                .collect(Collectors.toList());
    }

    /** Motivo por el que un partido NO puede ir a un slot (null = válido). Mismas reglas que isValidSlot (sin preferencias). */
    private String placementReason(Match match, TimeSlot slot, List<Match> scheduled,
                                   Tournament tournament, Map<Long, List<PairScheduleConstraint>> cons) {
        // Parejas efectivas: en placeholders de R2 (sin parejas aún), las 4 de la zona
        List<Long> pairIds = effectivePairIds(match);
        if (isCourtOccupied(slot, scheduled)) return "Cancha ocupada";
        int minInt = tournament.getMinIntervalMinutes();
        for (Long pid : pairIds) {
            if (isPairBusy(pid, slot, scheduled)) return "Una pareja ya juega a esa hora";
            if (violatesMinInterval(pid, slot, scheduled, minInt)) return "No respeta el intervalo mínimo de la pareja";
        }
        if (violatesZoneRoundOrder(match, slot, scheduled, pairIds)) return "La Ronda 2 debe ir después de la Ronda 1";
        if (match.getPhase() == MatchPhase.ZONE) {
            for (Long pid : pairIds) {
                if (violatesRestriction(slot, cons.getOrDefault(pid, List.of())))
                    return "Choca con una restricción horaria de la pareja";
            }
        }
        return null;
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
}
