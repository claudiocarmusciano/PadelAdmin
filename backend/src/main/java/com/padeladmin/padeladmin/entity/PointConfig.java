package com.padeladmin.padeladmin.entity;

import com.padeladmin.padeladmin.enums.TournamentStage;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "point_configs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class PointConfig {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "stage", length = 30)
    private TournamentStage stage;

    @Column(nullable = false)
    private int points;
}
