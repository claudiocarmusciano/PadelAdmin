package com.padeladmin.padeladmin.entity;

import com.padeladmin.padeladmin.enums.ConstraintType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Entity
@Table(name = "pair_schedule_constraints")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PairScheduleConstraint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pair_id", nullable = false)
    private Pair pair;

    @Enumerated(EnumType.STRING)
    @Column(name = "constraint_type", nullable = false, length = 15)
    private ConstraintType constraintType;

    // 0 = Lunes, 6 = Domingo
    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "slot_start", nullable = false)
    private LocalTime slotStart;

    @Column(name = "slot_end", nullable = false)
    private LocalTime slotEnd;
}
