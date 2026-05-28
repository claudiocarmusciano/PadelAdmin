package com.padeladmin.padeladmin.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Parches manuales al schema que Hibernate ddl-auto=update no aplica solo.
 *
 * Caso conocido: cuando el enum {@code UserRole} se amplió de [ADMIN] a [ADMIN, VIEWER],
 * Postgres mantuvo el CHECK constraint {@code users_role_check} con el set viejo,
 * impidiendo registrar usuarios VIEWER. Hibernate update no modifica constraints existentes.
 *
 * Esto se ejecuta una sola vez por arranque pero es idempotente.
 * Debe correr ANTES que AdminSeeder (Order menor).
 */
@Component
@RequiredArgsConstructor
@Order(0)
public class SchemaPatcher implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaPatcher.class);

    private final JdbcTemplate jdbc;

    @Override
    public void run(String... args) {
        patchUsersRoleCheck();
    }

    /** Drop + recreate del constraint users.role para que acepte ADMIN y VIEWER. */
    private void patchUsersRoleCheck() {
        try {
            jdbc.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
            jdbc.execute("ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'VIEWER'))");
            log.info("✓ Constraint users_role_check actualizado: acepta ADMIN y VIEWER");
        } catch (Exception ex) {
            // No frenamos el arranque si falla — logueamos para que sea visible.
            log.warn("No se pudo patchar users_role_check: {}", ex.getMessage());
        }
    }
}
