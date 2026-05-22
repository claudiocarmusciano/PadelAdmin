package com.padeladmin.padeladmin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "zones",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tournament_id", "name"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    // "A", "B", "C"...
    @Column(nullable = false, length = 10)
    private String name;

    // 3 o 4 parejas
    @Column(name = "zone_size", nullable = false)
    private Integer zoneSize;

    // Orden para construir el draw eliminatorio
    @Column(name = "zone_order", nullable = false)
    private Integer zoneOrder;

    @OneToMany(mappedBy = "zone", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ZonePair> zonePairs = new ArrayList<>();
}
