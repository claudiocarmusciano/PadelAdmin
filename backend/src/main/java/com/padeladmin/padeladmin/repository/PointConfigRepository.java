package com.padeladmin.padeladmin.repository;

import com.padeladmin.padeladmin.entity.PointConfig;
import com.padeladmin.padeladmin.enums.TournamentStage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointConfigRepository extends JpaRepository<PointConfig, TournamentStage> {
}
