package com.padeladmin.padeladmin.config;

import com.padeladmin.padeladmin.entity.Category;
import com.padeladmin.padeladmin.entity.Club;
import com.padeladmin.padeladmin.entity.Complex;
import com.padeladmin.padeladmin.entity.Tournament;
import com.padeladmin.padeladmin.repository.CategoryRepository;
import com.padeladmin.padeladmin.repository.ClubRepository;
import com.padeladmin.padeladmin.repository.ComplexRepository;
import com.padeladmin.padeladmin.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Migración Fase 1 (multi-tenancy): si hay datos existentes sin club asignado, crea "Club #1"
 * y le asigna los complejos, categorías y torneos actuales. Idempotente: solo corre si todavía
 * no existe ningún Club.
 *
 * IMPORTANTE: NO toca el rol del admin ni la seguridad (eso es Fase 2). Es puramente aditivo:
 * la app sigue funcionando igual; solo se rellena el club_id.
 */
@Component
@Order(20) // después de los seeders
@RequiredArgsConstructor
public class TenantMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantMigration.class);

    private final ClubRepository clubRepository;
    private final ComplexRepository complexRepository;
    private final CategoryRepository categoryRepository;
    private final TournamentRepository tournamentRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (clubRepository.count() > 0) {
            log.debug("Multi-tenancy: ya existen clubes, no se ejecuta la migración Fase 1.");
            return;
        }

        List<Complex> complexes = complexRepository.findAll();
        List<Category> categories = categoryRepository.findAll();
        List<Tournament> tournaments = tournamentRepository.findAll();

        boolean hayDatos = !complexes.isEmpty() || !categories.isEmpty() || !tournaments.isEmpty();
        if (!hayDatos) {
            log.info("Multi-tenancy: base vacía, no hay nada que migrar (los clubes se crearán por el super-admin).");
            return;
        }

        // Nombre del Club #1: el del primer complejo si existe, si no un default.
        String nombre = complexes.stream()
                .map(Complex::getName)
                .filter(n -> n != null && !n.isBlank())
                .findFirst()
                .orElse("Club Principal");

        Club club1 = clubRepository.save(Club.builder().name(nombre).active(true).build());
        log.info("Multi-tenancy: creado Club #1 '{}' (id {}).", club1.getName(), club1.getId());

        int c = 0, cat = 0, t = 0;
        for (Complex x : complexes) if (x.getClub() == null) { x.setClub(club1); complexRepository.save(x); c++; }
        for (Category x : categories) if (x.getClub() == null) { x.setClub(club1); categoryRepository.save(x); cat++; }
        for (Tournament x : tournaments) if (x.getClub() == null) { x.setClub(club1); tournamentRepository.save(x); t++; }

        log.info("Multi-tenancy: asignados a Club #1 → {} complejos, {} categorías, {} torneos.", c, cat, t);
    }
}
