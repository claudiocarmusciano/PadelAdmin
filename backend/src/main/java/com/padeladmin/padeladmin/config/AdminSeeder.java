package com.padeladmin.padeladmin.config;

import com.padeladmin.padeladmin.entity.User;
import com.padeladmin.padeladmin.enums.UserRole;
import com.padeladmin.padeladmin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (userRepository.existsByRole(UserRole.ADMIN)) {
            log.debug("Ya existe al menos un ADMIN, no se seedea otro.");
            return;
        }

        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.warn("No hay ADMIN en la base y las variables ADMIN_EMAIL/ADMIN_PASSWORD no están configuradas. " +
                    "Creá un admin manualmente o seteá las variables de entorno.");
            return;
        }

        String email = adminEmail.trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            log.warn("Existe un usuario con email {} pero no es ADMIN. No se sobreescribe.", email);
            return;
        }

        User admin = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(UserRole.ADMIN)
                .active(true)
                .build();
        userRepository.save(admin);
        log.info("✓ Admin inicial creado: {}", email);
    }
}
