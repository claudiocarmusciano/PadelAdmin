package com.padeladmin.padeladmin.repository;

import com.padeladmin.padeladmin.entity.Court;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourtRepository extends JpaRepository<Court, Long> {
    List<Court> findByComplexIdAndActiveTrue(Long complexId);
    List<Court> findByComplexIdOrderByNameAsc(Long complexId);
    boolean existsByComplexIdAndNameIgnoreCase(Long complexId, String name);
}
