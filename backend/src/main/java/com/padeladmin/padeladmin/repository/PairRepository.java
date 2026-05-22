package com.padeladmin.padeladmin.repository;

import com.padeladmin.padeladmin.entity.Pair;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PairRepository extends JpaRepository<Pair, Long> {
    List<Pair> findByTournamentIdOrderByTotalPointsDesc(Long tournamentId);
    long countByTournamentId(Long tournamentId);
}
