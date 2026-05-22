package com.padeladmin.padeladmin.repository;

import com.padeladmin.padeladmin.entity.MatchResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MatchResultRepository extends JpaRepository<MatchResult, Long> {
    Optional<MatchResult> findByMatchId(Long matchId);
    boolean existsByMatchId(Long matchId);
}
