package com.padeladmin.padeladmin.repository;

import com.padeladmin.padeladmin.entity.MatchSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchSetRepository extends JpaRepository<MatchSet, Long> {
    List<MatchSet> findByMatchResultIdOrderBySetNumber(Long matchResultId);
}
