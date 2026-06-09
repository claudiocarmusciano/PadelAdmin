package com.padeladmin.padeladmin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "players")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    // Identidad compartida entre clubes (multi-tenancy).
    // DNI = clave única (obligatorio en altas nuevas; legacy nullable). Email = canal de activación/recuperación.
    @Column(length = 20)
    private String dni;

    @Column(length = 255)
    private String email;

    // true cuando el jugador seteó su contraseña y puede loguearse.
    // columnDefinition con default: permite agregar la columna a tablas que ya tienen filas.
    @Column(name = "account_activated", nullable = false, columnDefinition = "boolean not null default false")
    @Builder.Default
    private boolean accountActivated = false;

    @Column(length = 30)
    private String phone;

    @Column(name = "telegram_chat_id", length = 50)
    private String telegramChatId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "player", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PlayerCategoryPoints> categoryPoints = new ArrayList<>();
}
