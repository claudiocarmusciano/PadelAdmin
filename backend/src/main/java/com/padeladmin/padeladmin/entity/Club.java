package com.padeladmin.padeladmin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Tenant del sistema: cada club gestiona SOLO sus propios datos (categorías, torneos,
 * complejos/canchas, turnos, ranking). Lo único compartido entre clubes es el padrón
 * global de jugadores (entidad Player, que NO tiene club).
 */
@Entity
@Table(name = "clubs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Club {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 255)
    private String address;

    @Column(length = 30)
    private String phone;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
