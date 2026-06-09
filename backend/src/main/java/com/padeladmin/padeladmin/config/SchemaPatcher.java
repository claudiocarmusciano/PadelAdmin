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
        dropCategoriesGlobalNameUnique();
    }

    /**
     * Multi-tenant: las categorías dejaron de ser únicas por nombre global (cada club tiene
     * las suyas, p.ej. dos clubes con "7ma. Caballeros"). La entidad ya no declara unique=true,
     * pero ddl-auto=update no borra el constraint viejo: lo buscamos por definición (UNIQUE
     * sobre la columna name) y lo borramos. La unicidad por club la valida CategoryService.
     */
    private void dropCategoriesGlobalNameUnique() {
        try {
            var names = jdbc.queryForList("""
                    SELECT con.conname
                    FROM pg_constraint con
                    JOIN pg_class rel ON rel.oid = con.conrelid
                    WHERE rel.relname = 'categories'
                      AND con.contype = 'u'
                      AND (SELECT array_agg(att.attname)
                           FROM unnest(con.conkey) k
                           JOIN pg_attribute att ON att.attrelid = rel.oid AND att.attnum = k
                          ) = ARRAY['name']::name[]
                    """, String.class);
            for (String name : names) {
                jdbc.execute("ALTER TABLE categories DROP CONSTRAINT IF EXISTS \"" + name + "\"");
                log.info("✓ Constraint único global de categories.name eliminado: {}", name);
            }
        } catch (Exception ex) {
            log.warn("No se pudo patchar el unique de categories.name: {}", ex.getMessage());
        }
    }

    /** Drop + recreate del constraint users.role para que acepte ADMIN y VIEWER. */
    private void patchUsersRoleCheck() {
        try {
            jdbc.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
            jdbc.execute("ALTER TABLE users ADD CONSTRAINT users_role_check " +
                    "CHECK (role IN ('ADMIN', 'VIEWER', 'SUPER_ADMIN', 'CLUB', 'PLAYER'))");
            log.info("✓ Constraint users_role_check actualizado: ADMIN, VIEWER, SUPER_ADMIN, CLUB, PLAYER");
        } catch (Exception ex) {
            // No frenamos el arranque si falla — logueamos para que sea visible.
            log.warn("No se pudo patchar users_role_check: {}", ex.getMessage());
        }
    }
}
