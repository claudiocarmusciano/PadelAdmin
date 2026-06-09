package com.padeladmin.padeladmin.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "categories")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tenant dueño. Nullable durante migración; se backfillea a "Club #1".
    // Las categorías son POR CLUB (la unicidad del nombre pasa a ser por club, no global).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id")
    private Club club;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 255)
    private String description;
}
