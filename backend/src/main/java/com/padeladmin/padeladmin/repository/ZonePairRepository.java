package com.padeladmin.padeladmin.repository;

import com.padeladmin.padeladmin.entity.ZonePair;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ZonePairRepository extends JpaRepository<ZonePair, Long> {
    List<ZonePair> findByZoneIdOrderByPosition(Long zoneId);
    Optional<ZonePair> findByZoneIdAndPairId(Long zoneId, Long pairId);
    Optional<ZonePair> findByPairIdAndZoneTournamentId(Long pairId, Long tournamentId);
    void deleteByZoneId(Long zoneId);
}
