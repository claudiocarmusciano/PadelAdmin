package com.padeladmin.padeladmin.entity;

import com.padeladmin.padeladmin.enums.TournamentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

@Entity
@Table(name = "tournaments")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Tournament {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tenant dueño (multi-tenancy). Nullable durante migración; se backfillea a "Club #1".
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id")
    private Club club;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complex_id")
    private Complex complex;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "match_duration_minutes", nullable = false)
    @Builder.Default
    private Integer matchDurationMinutes = 60;

    // Tiempo mínimo entre el fin de un partido y el inicio del siguiente (misma pareja)
    @Column(name = "min_interval_minutes", nullable = false)
    @Builder.Default
    private Integer minIntervalMinutes = 180;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TournamentStatus status = TournamentStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Días de semana en que se juegan los partidos de zona (1=Lun … 7=Dom). Vacío = todos los días disponibles.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tournament_zone_days",
            joinColumns = @JoinColumn(name = "tournament_id"))
    @Column(name = "day_of_week")
    @Builder.Default
    private List<Integer> zoneDays = new ArrayList<>();

    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TournamentBuffer> buffers = new ArrayList<>();

    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Pair> pairs = new ArrayList<>();

    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Zone> zones = new ArrayList<>();
}
