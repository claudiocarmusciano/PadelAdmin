package com.padeladmin.padeladmin.repository;

import com.padeladmin.padeladmin.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneRepository extends JpaRepository<Zone, Long> {
    List<Zone> findByTournamentIdOrderByZoneOrder(Long tournamentId);
}
