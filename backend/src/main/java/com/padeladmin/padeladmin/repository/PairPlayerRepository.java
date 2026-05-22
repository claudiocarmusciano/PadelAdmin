package com.padeladmin.padeladmin.repository;

import com.padeladmin.padeladmin.entity.PairPlayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PairPlayerRepository extends JpaRepository<PairPlayer, Long> {
    List<PairPlayer> findByPairId(Long pairId);
    boolean existsByPairIdAndPlayerId(Long pairId, Long playerId);
}
