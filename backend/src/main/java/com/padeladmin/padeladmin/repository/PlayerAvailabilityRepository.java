package com.padeladmin.padeladmin.repository;

import com.padeladmin.padeladmin.entity.PlayerAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlayerAvailabilityRepository extends JpaRepository<PlayerAvailability, Long> {
    List<PlayerAvailability> findByWindowIdAndPlayerId(Long windowId, Long playerId);
    List<PlayerAvailability> findByWindowId(Long windowId);
    void deleteByWindowIdAndPlayerId(Long windowId, Long playerId);
}
