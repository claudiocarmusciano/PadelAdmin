package com.padeladmin.padeladmin.entity;

import com.padeladmin.padeladmin.enums.WindowStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "availability_windows")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AvailabilityWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    // Ej: "Ronda 1 - Zona A y B"
    @Column(length = 100)
    private String label;

    @Column(name = "opens_at", nullable = false)
    private LocalDateTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalDateTime closesAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WindowStatus status = WindowStatus.OPEN;

    @OneToMany(mappedBy = "window", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PlayerAvailability> playerAvailabilities = new ArrayList<>();
}
