package com.padeladmin.padeladmin.security;

import com.padeladmin.padeladmin.enums.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Acceso al contexto multi-tenant del request actual. Los roles globales (ADMIN, VIEWER,
 * SUPER_ADMIN) no tienen club: ven todo. El rol CLUB queda restringido a su clubId.
 */
@Component
public class TenantContext {

    public Optional<AppPrincipal> principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    /** clubId al que está restringido el usuario actual; empty = acceso global. */
    public Optional<Long> restrictedClubId() {
        return principal()
                .filter(p -> p.getRole() == UserRole.CLUB && p.getClubId() != null)
                .map(AppPrincipal::getClubId);
    }

    /** true si el recurso (con ese clubId, posiblemente null) es visible para el usuario actual. */
    public boolean canAccessClub(Long resourceClubId) {
        return restrictedClubId()
                .map(restricted -> restricted.equals(resourceClubId))
                .orElse(true);
    }
}
