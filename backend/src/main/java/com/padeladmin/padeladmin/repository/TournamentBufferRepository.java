package com.padeladmin.padeladmin.repository;

import com.padeladmin.padeladmin.entity.TournamentBuffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TournamentBufferRepository extends JpaRepository<TournamentBuffer, Long> {
    List<TournamentBuffer> findByTournamentIdOrderByDayOfWeek(Long tournamentId);
    void deleteByTournamentId(Long tournamentId);
}
