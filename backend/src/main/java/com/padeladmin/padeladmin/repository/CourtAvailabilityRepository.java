package com.padeladmin.padeladmin.repository;

import com.padeladmin.padeladmin.entity.CourtAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourtAvailabilityRepository extends JpaRepository<CourtAvailability, Long> {
    List<CourtAvailability> findByCourtIdOrderByDayOfWeek(Long courtId);
    Optional<CourtAvailability> findByCourtIdAndDayOfWeek(Long courtId, Integer dayOfWeek);
    boolean existsByCourtIdAndDayOfWeek(Long courtId, Integer dayOfWeek);
}
