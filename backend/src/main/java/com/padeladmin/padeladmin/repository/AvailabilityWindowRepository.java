package com.padeladmin.padeladmin.repository;

import com.padeladmin.padeladmin.entity.AvailabilityWindow;
import com.padeladmin.padeladmin.enums.WindowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AvailabilityWindowRepository extends JpaRepository<AvailabilityWindow, Long> {
    List<AvailabilityWindow> findByTournamentIdAndStatus(Long tournamentId, WindowStatus status);
    List<AvailabilityWindow> findByTournamentIdOrderByOpensAtDesc(Long tournamentId);
}
