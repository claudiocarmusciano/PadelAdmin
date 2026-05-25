package com.padeladmin.padeladmin.service;

import com.padeladmin.padeladmin.dto.fixture.FixtureResponseDto;
import com.padeladmin.padeladmin.dto.fixture.MatchPairDto;
import com.padeladmin.padeladmin.dto.fixture.MatchResponseDto;
import com.padeladmin.padeladmin.entity.*;
import com.padeladmin.padeladmin.enums.ConstraintType;
import com.padeladmin.padeladmin.enums.MatchPhase;
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
                    "No se puede regenerar el fixture: ya hay partidos con resultado registrado. " +
                    "Use POST /api/tournaments/{id}/fixture/schedule-pending para programar partidos pendientes.");
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
            // Los partidos de ronda 2 (ganador vs ganador, perdedor vs perdedor)
            // se crean automáticamente al registrar los resultados de ronda 1
            Match m1 = buildMatch(tournament, zone, pairAt(zonePairs, 1), pairAt(zonePairs, 4));
            m1.setZoneRound(1);
            Match m2 = buildMatch(tournament, zone, pairAt(zonePairs, 2), pairAt(zonePairs, 3));
            m2.setZoneRound(1);
            matches.add(m1);
            matches.add(m2);
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

        while (!cursor.isAfter(closeTime.minusMinutes(matchDurationMinutes))) {
            LocalTime slotEnd = cursor.plusMinutes(matchDurationMinutes);

            if (!overlapsAnyBuffer(cursor, slotEnd, dayBuffers)) {
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

        for (Match match : matches) {
            TimeSlot chosen = null;

            // Pasada 1: intenta respetar preferencias (soft constraints ON)
            for (TimeSlot slot : slots) {
                if (isValidSlot(match, slot, scheduled, tournament, constraintsByPairId, true)) {
                    chosen = slot;
                    break;
                }
            }

            // Pasada 2: si no encontró, relaja preferencias (solo hard constraints)
            if (chosen == null) {
                log.debug("Match {}: no hay slot con preferencias, relajando...", match);
                for (TimeSlot slot : slots) {
                    if (isValidSlot(match, slot, scheduled, tournament, constraintsByPairId, false)) {
                        chosen = slot;
                        break;
                    }
                }
            }

            if (chosen != null) {
                match.setScheduledStart(LocalDateTime.of(chosen.getDate(), chosen.getStartTime()));
                match.setScheduledEnd(LocalDateTime.of(chosen.getDate(), chosen.getEndTime()));
                match.setCourt(courtMap.get(chosen.getCourtId()));
                match.setStatus(MatchStatus.SCHEDULED);
                scheduled.add(match);
            } else {
                log.warn("No se pudo programar el partido entre pareja {} y pareja {}",
                        match.getPair1().getId(),
                        match.getPair2() != null ? match.getPair2().getId() : "BYE");
            }
        }
    }

    private boolean isValidSlot(Match match,
                                  TimeSlot slot,
                                  List<Match> scheduled,
                                  Tournament tournament,
                                  Map<Long, List<PairScheduleConstraint>> constraintsByPairId,
                                  boolean enforcePreferences) {

        Long pair1Id = match.getPair1().getId();
        Long pair2Id = match.getPair2() != null ? match.getPair2().getId() : null;
        int minInterval = tournament.getMinIntervalMinutes();

        // 1. Cancha no ocupada en ese slot
        if (isCourtOccupied(slot, scheduled)) return false;

        // 2. Ninguna pareja tiene otro partido en ese slot (concurrencia)
        if (isPairBusy(pair1Id, slot, scheduled)) return false;
        if (pair2Id != null && isPairBusy(pair2Id, slot, scheduled)) return false;

        // 3. Intervalo mínimo entre partidos de la misma pareja
        if (violatesMinInterval(pair1Id, slot, scheduled, minInterval)) return false;
        if (pair2Id != null && violatesMinInterval(pair2Id, slot, scheduled, minInterval)) return false;

        // 4. Restricciones y preferencias: solo aplican a partidos de zona
        if (match.getPhase() == MatchPhase.ZONE) {
            List<PairScheduleConstraint> constraints1 = constraintsByPairId.getOrDefault(pair1Id, List.of());
            List<PairScheduleConstraint> constraints2 = pair2Id != null
                    ? constraintsByPairId.getOrDefault(pair2Id, List.of())
                    : List.of();

            if (violatesRestriction(slot, constraints1)) return false;
            if (violatesRestriction(slot, constraints2)) return false;

            if (enforcePreferences) {
                boolean pair1HasPrefs = hasPreferences(constraints1);
                boolean pair2HasPrefs = hasPreferences(constraints2);

                if (pair1HasPrefs && !satisfiesPreference(slot, constraints1)) return false;
                if (pair2HasPrefs && !satisfiesPreference(slot, constraints2)) return false;
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
        LocalTime slotEnd = slot.getEndTime();

        return scheduled.stream()
                .filter(m -> involvesPair(m, pairId)
                        && m.getScheduledStart().toLocalDate().equals(slotDate))
                .anyMatch(m -> {
                    LocalTime prevEnd = m.getScheduledEnd().toLocalTime();
                    LocalTime prevStart = m.getScheduledStart().toLocalTime();
                    // El nuevo slot empieza demasiado cerca del final de un partido anterior
                    // Usamos !isBefore para cubrir también el caso slotStart == prevEnd (back-to-back)
                    boolean tooCloseAfter = !slotStart.isBefore(prevEnd)
                            && slotStart.isBefore(prevEnd.plusMinutes(minIntervalMinutes));
                    // Un partido ya programado empieza demasiado cerca del final del nuevo slot
                    boolean tooCloseBefore = !prevStart.isBefore(slotEnd)
                            && prevStart.isBefore(slotEnd.plusMinutes(minIntervalMinutes));
                    return tooCloseAfter || tooCloseBefore;
                });
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
        return constraints.stream()
                .filter(c -> c.getConstraintType() == ConstraintType.PREFERENCE)
                .filter(c -> c.getDayOfWeek().equals(dow))
                .anyMatch(c -> timesOverlap(c.getSlotStart(), c.getSlotEnd(),
                        slot.getStartTime(), slot.getEndTime()));
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
        for (Match m : matches) {
            if (m.getPair1() != null) pairIds.add(m.getPair1().getId());
            if (m.getPair2() != null) pairIds.add(m.getPair2().getId());
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

    private Tournament getTournamentOrThrow(Long id) {
        return tournamentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", id));
    }

    // ── Mapeo a DTO ───────────────────────────────────────────────────────────

    private FixtureResponseDto buildResponse(Long tournamentId, List<Match> matches) {
        List<MatchResponseDto> matchDtos = matches.stream()
                .map(this::toMatchDto)
                .toList();

        long scheduledCount = matches.stream().filter(m -> m.getStatus() == MatchStatus.SCHEDULED).count();

        return FixtureResponseDto.builder()
                .tournamentId(tournamentId)
                .totalMatches(matches.size())
                .scheduledCount((int) scheduledCount)
                .pendingCount(matches.size() - (int) scheduledCount)
                .matches(matchDtos)
                .build();
    }

    private MatchResponseDto toMatchDto(Match match) {
        MatchResponseDto.MatchResponseDtoBuilder builder = MatchResponseDto.builder()
                .id(match.getId())
                .zoneName(match.getZone() != null ? "Zona " + match.getZone().getName() : null)
                .eliminationRound(match.getEliminationRound())
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

            // Determinar estado según disponibilidad de información
            if (match.getScheduledStart() != null) {
                match.setStatus(MatchStatus.SCHEDULED);
            }
        }

        matchRepository.save(match);
        log.info("Cancha/horario actualizado: Match {} → Court {}, Start {}",
                matchId, courtId, scheduledStart);
        return toMatchDto(match);
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
