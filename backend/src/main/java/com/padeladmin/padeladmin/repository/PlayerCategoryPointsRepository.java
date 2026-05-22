package com.padeladmin.padeladmin.repository;

import com.padeladmin.padeladmin.entity.PlayerCategoryPoints;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerCategoryPointsRepository extends JpaRepository<PlayerCategoryPoints, Long> {
    Optional<PlayerCategoryPoints> findByPlayerIdAndCategoryId(Long playerId, Long categoryId);
    List<PlayerCategoryPoints> findByPlayerId(Long playerId);
}
