package com.padeladmin.padeladmin.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Singleton — siempre existe una única fila con id = 1.
 */
@Entity
@Table(name = "global_settings")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class GlobalSettings {

    @Id
    private Long id = 1L;

    /** Duración por defecto de los partidos (minutos) */
    @Column(nullable = false)
    private int defaultMatchDurationMinutes = 90;

    /** Pausa mínima entre dos partidos de zona de la misma pareja (minutos) */
    @Column(nullable = false)
    private int defaultMinIntervalMinutes = 60;
}
