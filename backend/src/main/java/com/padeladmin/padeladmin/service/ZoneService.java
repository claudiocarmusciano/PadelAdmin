package com.padeladmin.padeladmin.service;

import com.padeladmin.padeladmin.dto.zone.MovePairRequestDto;
import com.padeladmin.padeladmin.dto.zone.SwapPairsRequestDto;
import com.padeladmin.padeladmin.dto.zone.ZonePairResponseDto;
import com.padeladmin.padeladmin.dto.zone.ZoneResponseDto;
import com.padeladmin.padeladmin.entity.*;
import com.padeladmin.padeladmin.exception.BusinessException;
import com.padeladmin.padeladmin.exception.ResourceNotFoundException;
import com.padeladmin.padeladmin.enums.MatchPhase;
import com.padeladmin.padeladmin.enums.MatchStatus;
import com.padeladmin.padeladmin.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ZoneService {

    private final ZoneRepository zoneRepository;
    private final ZonePairRepository zonePairRepository;
    private final PairRepository pairRepository;
    private final PairPlayerRepository pairPlayerRepository;
    private final TournamentRepository tournamentRepository;
    private final MatchRepository matchRepository;

    private static final int MIN_PAIRS = 9;

    // ── Generación automática de zonas ────────────────────────────────────────

    @Transactional
    public List<ZoneResponseDto> generateZones(Long tournamentId) {
        Tournament tournament = getTournamentOrThrow(tournamentId);

        List<Pair> pairs = pairRepository.findByTournamentIdOrderByTotalPointsDesc(tournamentId);
        int N = pairs.size();

        if (N < MIN_PAIRS) {
            throw new BusinessException(
                    "Se necesitan al menos " + MIN_PAIRS + " parejas para generar zonas. " +
                    "Actualmente hay " + N + ".");
        }

        // Si ya hay partidos de zona jugados, no se puede regenerar
        List<Match> existingMatches = matchRepository.findByTournamentIdAndPhase(tournamentId, MatchPhase.ZONE);
        boolean anyPlayed = existingMatches.stream()
                .anyMatch(m -> m.getStatus() == MatchStatus.PLAYED);
        if (anyPlayed) {
            throw new BusinessException(
                    "No se pueden regenerar las zonas: ya hay partidos con resultado registrado.");
        }
        // Eliminar partidos de zona sin resultado (el fixture se deberá regenerar)
        matchRepository.deleteAll(existingMatches);
        matchRepository.flush(); // forzar DELETE antes de tocar zonas (FK: matches.zone_id → zones.id)

        // Eliminar zonas existentes — el cascade ALL en Zone.zonePairs borra los ZonePair automáticamente
        List<Zone> existingZones = zoneRepository.findByTournamentIdOrderByZoneOrder(tournamentId);
        zoneRepository.deleteAll(existingZones);
        zoneRepository.flush();

        // Calcular estructura: minimizar zonas de 4
        // N % 3 == 0 → 0 zonas de 4
        // N % 3 == 1 → 1 zona de 4
        // N % 3 == 2 → 2 zonas de 4
        int nFours  = N % 3;
        int nThrees = (N - 4 * nFours) / 3;
        int totalZones = nFours + nThrees;

        // Crear zonas (las primeras nFours son de 4 pares)
        List<Zone> zones = new ArrayList<>();
        for (int i = 0; i < totalZones; i++) {
            String name = String.valueOf((char) ('A' + i));
            int size = (i < nFours) ? 4 : 3;
            Zone zone = Zone.builder()
                    .tournament(tournament)
                    .name(name)
                    .zoneSize(size)
                    .zoneOrder(i + 1)
                    .build();
            zones.add(zoneRepository.save(zone));
        }

        // Tracking de posición actual por zona
        int[] nextPos = new int[totalZones];
        for (int i = 0; i < totalZones; i++) nextPos[i] = 1;

        int pairIdx = 0;

        // Fase 1: Cabezas de zona (1° de cada zona en orden)
        for (int zi = 0; zi < totalZones && pairIdx < N; zi++) {
            saveZonePair(pairs.get(pairIdx++), zones.get(zi), nextPos[zi]++);
        }

        // Fase 2: Snake inverso (segunda posición, de la última zona a la primera)
        for (int zi = totalZones - 1; zi >= 0 && pairIdx < N; zi--) {
            saveZonePair(pairs.get(pairIdx++), zones.get(zi), nextPos[zi]++);
        }

        // Fase 3: Restantes — llenar zonas con cupo disponible en orden
        for (int zi = 0; zi < totalZones && pairIdx < N; zi++) {
            Zone zone = zones.get(zi);
            while (nextPos[zi] <= zone.getZoneSize() && pairIdx < N) {
                saveZonePair(pairs.get(pairIdx++), zone, nextPos[zi]++);
            }
        }

        return zones.stream().map(this::toDto).toList();
    }

    // ── Consultas ─────────────────────────────────────────────────────────────

    public List<ZoneResponseDto> findByTournament(Long tournamentId) {
        getTournamentOrThrow(tournamentId);
        return zoneRepository.findByTournamentIdOrderByZoneOrder(tournamentId).stream()
                .map(this::toDto)
                .toList();
    }

    public ZoneResponseDto findById(Long tournamentId, Long zoneId) {
        Zone zone = getZoneOrThrow(tournamentId, zoneId);
        return toDto(zone);
    }

    // ── Ajuste manual: mover una pareja a otra zona ───────────────────────────

    @Transactional
    public List<ZoneResponseDto> movePair(Long tournamentId, Long pairId, MovePairRequestDto dto) {
        getTournamentOrThrow(tournamentId);

        // No se puede mover parejas si ya hay resultados registrados
        boolean anyPlayed = matchRepository
                .findByTournamentIdAndPhase(tournamentId, MatchPhase.ZONE)
                .stream().anyMatch(m -> m.getStatus() == MatchStatus.PLAYED);
        if (anyPlayed) {
            throw new BusinessException(
                    "No se pueden mover parejas: ya hay partidos con resultado registrado.");
        }

        // Buscar la zona actual de la pareja
        ZonePair currentZonePair = zonePairRepository
                .findByPairIdAndZoneTournamentId(pairId, tournamentId)
                .orElseThrow(() -> new BusinessException(
                        "La pareja no está asignada a ninguna zona"));

        Zone targetZone = getZoneOrThrow(tournamentId, dto.getTargetZoneId());

        if (currentZonePair.getZone().getId().equals(targetZone.getId())) {
            throw new BusinessException("La pareja ya se encuentra en esa zona");
        }

        Zone sourceZone = currentZonePair.getZone();

        // Verificar que la zona destino tiene espacio
        long targetCount = zonePairRepository.findByZoneIdOrderByPosition(targetZone.getId()).size();
        if (targetCount >= targetZone.getZoneSize()) {
            throw new BusinessException(
                    "La Zona " + targetZone.getName() + " ya está completa (" +
                    targetZone.getZoneSize() + " parejas)");
        }

        // Mover: quitar de zona origen y agregar a zona destino
        int newPosition = (int) targetCount + 1;
        Pair pair = currentZonePair.getPair();
        zonePairRepository.delete(currentZonePair);

        // Reordenar posiciones en zona origen
        reorderPositions(sourceZone.getId());

        saveZonePair(pair, targetZone, newPosition);

        return findByTournament(tournamentId);
    }

    // ── Intercambio de parejas entre zonas ───────────────────────────────────

    @Transactional
    public List<ZoneResponseDto> swapPairs(Long tournamentId, Long pairId, SwapPairsRequestDto dto) {
        getTournamentOrThrow(tournamentId);

        // No se puede intercambiar si ya hay resultados registrados
        boolean anyPlayed = matchRepository
                .findByTournamentIdAndPhase(tournamentId, MatchPhase.ZONE)
                .stream().anyMatch(m -> m.getStatus() == MatchStatus.PLAYED);
        if (anyPlayed) {
            throw new BusinessException(
                    "No se pueden intercambiar parejas: ya hay partidos con resultado registrado.");
        }

        ZonePair sourceZP = zonePairRepository
                .findByPairIdAndZoneTournamentId(pairId, tournamentId)
                .orElseThrow(() -> new BusinessException("La pareja origen no está asignada a ninguna zona"));

        ZonePair targetZP = zonePairRepository
                .findByPairIdAndZoneTournamentId(dto.getTargetPairId(), tournamentId)
                .orElseThrow(() -> new BusinessException("La pareja destino no está asignada a ninguna zona"));

        if (sourceZP.getZone().getId().equals(targetZP.getZone().getId())) {
            throw new BusinessException("Las parejas ya están en la misma zona");
        }

        // Capturar datos antes de borrar
        Zone sourceZone = sourceZP.getZone();
        Zone targetZone = targetZP.getZone();
        int sourcePos  = sourceZP.getPosition();
        int targetPos  = targetZP.getPosition();
        Pair sourcePair = sourceZP.getPair();
        Pair targetPair = targetZP.getPair();

        // Borrar ambos y forzar DELETE antes de insertar (evita violación de UniqueConstraint)
        zonePairRepository.delete(sourceZP);
        zonePairRepository.delete(targetZP);
        zonePairRepository.flush();

        // Recrear con zonas intercambiadas (cada pareja toma la posición de la otra)
        saveZonePair(sourcePair, targetZone, targetPos);
        saveZonePair(targetPair, sourceZone, sourcePos);

        return findByTournament(tournamentId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void saveZonePair(Pair pair, Zone zone, int position) {
        ZonePair zp = ZonePair.builder()
                .zone(zone)
                .pair(pair)
                .position(position)
                .build();
        zonePairRepository.save(zp);
    }

    private void reorderPositions(Long zoneId) {
        List<ZonePair> zonePairs = zonePairRepository.findByZoneIdOrderByPosition(zoneId);
        for (int i = 0; i < zonePairs.size(); i++) {
            ZonePair zp = zonePairs.get(i);
            zp.setPosition(i + 1);
            zonePairRepository.save(zp);
        }
    }

    private Tournament getTournamentOrThrow(Long id) {
        return tournamentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", id));
    }

    private Zone getZoneOrThrow(Long tournamentId, Long zoneId) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zona", zoneId));
        if (!zone.getTournament().getId().equals(tournamentId)) {
            throw new BusinessException("La zona no pertenece al torneo indicado");
        }
        return zone;
    }

    // ── Mapeo a DTO ───────────────────────────────────────────────────────────

    private ZoneResponseDto toDto(Zone zone) {
        List<ZonePair> zonePairs = zonePairRepository.findByZoneIdOrderByPosition(zone.getId());

        List<ZonePairResponseDto> pairDtos = zonePairs.stream()
                .map(zp -> {
                    List<PairPlayer> players = pairPlayerRepository.findByPairId(zp.getPair().getId());
                    String p1 = players.size() > 0
                            ? players.get(0).getPlayer().getLastName() + ", " + players.get(0).getPlayer().getFirstName()
                            : "-";
                    String p2 = players.size() > 1
                            ? players.get(1).getPlayer().getLastName() + ", " + players.get(1).getPlayer().getFirstName()
                            : "-";
                    return ZonePairResponseDto.builder()
                            .position(zp.getPosition())
                            .pairId(zp.getPair().getId())
                            .totalPoints(zp.getPair().getTotalPoints())
                            .player1(p1)
                            .player2(p2)
                            .build();
                })
                .toList();

        double totalZonePoints = pairDtos.stream()
                .mapToDouble(ZonePairResponseDto::getTotalPoints)
                .sum();

        return ZoneResponseDto.builder()
                .id(zone.getId())
                .name(zone.getName())
                .zoneSize(zone.getZoneSize())
                .zoneOrder(zone.getZoneOrder())
                .totalZonePoints(totalZonePoints)
                .pairs(pairDtos)
                .build();
    }
}
