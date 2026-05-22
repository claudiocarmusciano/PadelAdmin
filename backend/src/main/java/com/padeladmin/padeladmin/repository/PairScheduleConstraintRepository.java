package com.padeladmin.padeladmin.repository;

import com.padeladmin.padeladmin.entity.PairScheduleConstraint;
import com.padeladmin.padeladmin.enums.ConstraintType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PairScheduleConstraintRepository extends JpaRepository<PairScheduleConstraint, Long> {
    List<PairScheduleConstraint> findByPairId(Long pairId);
    List<PairScheduleConstraint> findByPairIdAndConstraintType(Long pairId, ConstraintType constraintType);
}
