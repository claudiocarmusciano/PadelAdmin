package com.padeladmin.padeladmin.repository;

import com.padeladmin.padeladmin.entity.Tournament;
import com.padeladmin.padeladmin.enums.TournamentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {
    List<Tournament> findByStatus(TournamentStatus status);
    List<Tournament> findByCategoryId(Long categoryId);
    List<Tournament> findAllByOrderByCreatedAtDesc();
}
