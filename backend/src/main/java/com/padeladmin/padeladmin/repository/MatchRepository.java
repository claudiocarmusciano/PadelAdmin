package com.padeladmin.padeladmin.repository;

import com.padeladmin.padeladmin.entity.Match;
import com.padeladmin.padeladmin.enums.MatchPhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findByTournamentIdAndPhase(Long tournamentId, MatchPhase phase);

    boolean existsByTournamentId(Long tournamentId);

    List<Match> findByZoneId(Long zoneId);

    List<Match> findByZoneIdAndZoneRound(Long zoneId, Integer zoneRound);

    Optional<Match> findByTournamentIdAndPhaseAndEliminationRoundAndBracketSlot(
            Long tournamentId, MatchPhase phase, Integer eliminationRound, Integer bracketSlot);

    @Query("""
            SELECT m FROM Match m
            WHERE (m.pair1.id = :pairId OR m.pair2.id = :pairId)
            AND m.tournament.id = :tournamentId
            ORDER BY m.scheduledStart ASC NULLS LAST
            """)
    List<Match> findByPairAndTournament(@Param("pairId") Long pairId,
                                        @Param("tournamentId") Long tournamentId);
}
