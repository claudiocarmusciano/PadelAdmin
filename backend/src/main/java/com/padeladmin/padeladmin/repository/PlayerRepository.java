package com.padeladmin.padeladmin.repository;

import com.padeladmin.padeladmin.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCase(
            String lastName, String firstName);

    // Verifica que un jugador no esté en dos parejas del mismo torneo
    @Query("""
            SELECT CASE WHEN COUNT(pp) > 0 THEN true ELSE false END
            FROM PairPlayer pp
            JOIN pp.pair p
            WHERE pp.player.id = :playerId
            AND p.tournament.id = :tournamentId
            """)
    boolean existsPlayerInTournament(@Param("playerId") Long playerId,
                                     @Param("tournamentId") Long tournamentId);
}
