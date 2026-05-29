package com.padeladmin.padeladmin.repository;

import com.padeladmin.padeladmin.entity.TournamentPointAward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TournamentPointAwardRepository extends JpaRepository<TournamentPointAward, Long> {
    boolean existsByTournamentId(Long tournamentId);
    List<TournamentPointAward> findByTournamentId(Long tournamentId);
    List<TournamentPointAward> findByPlayerIdOrderByAwardedAtDesc(Long playerId);
}
