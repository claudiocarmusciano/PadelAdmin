package com.padeladmin.padeladmin.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "player_category_points",
        uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "category_id"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PlayerCategoryPoints {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    @Builder.Default
    private Double points = 0.0;
}
