package com.padeladmin.padeladmin.config;

import com.padeladmin.padeladmin.entity.User;
import com.padeladmin.padeladmin.enums.UserRole;
import com.padeladmin.padeladmin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Crea (si no existe) el usuario invitado de solo lectura (rol VIEWER).
 * Se usa para el botón "Ingresar como invitado": el endpoint /api/auth/guest
 * emite un token para este usuario sin pedir contraseña. La contraseña es
 * aleatoria y nunca se usa para login directo.
 */
@Component
@RequiredArgsConstructor
public class GuestSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(GuestSeeder.class);

    /** Email del usuario invitado de solo lectura. */
    public static final String GUEST_EMAIL = "invitado@padel.com";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(GUEST_EMAIL)) {
            log.debug("Usuario invitado ya existe.");
            return;
        }
        User guest = User.builder()
                .email(GUEST_EMAIL)
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(UserRole.VIEWER)
                .active(true)
                .build();
        userRepository.save(guest);
        log.info("✓ Usuario invitado (VIEWER) creado: {}", GUEST_EMAIL);
    }
}
