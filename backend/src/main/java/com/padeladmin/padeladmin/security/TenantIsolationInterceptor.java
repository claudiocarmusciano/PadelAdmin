package com.padeladmin.padeladmin.security;

import com.padeladmin.padeladmin.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * Aislamiento multi-tenant: si el usuario es de rol CLUB, todo recurso referenciado por id en la
 * URL debe pertenecer a su club. Si no pertenece (o no tiene club), respondemos 404 — igual que
 * si no existiera — para no revelar datos de otros clubes.
 *
 * Cubre los ids raíz de cada árbol de recursos:
 *  - /api/tournaments/{id|tournamentId}/** → tournaments.club_id (zonas, parejas, fixture,
 *    llaves, buffers cuelgan del torneo y quedan cubiertos por este chequeo)
 *  - /api/matches/{matchId}/**             → tournaments.club_id vía matches
 *  - /api/zones/{zoneId}/**                → tournaments.club_id vía zones
 *  - /api/complexes/{id|complexId}/**      → complexes.club_id (canchas incluidas)
 *  - /api/courts/{courtId}/**              → complexes.club_id vía courts
 *  - /api/categories/{id}                  → categories.club_id
 *
 * El filtrado de los listados (sin id en la URL) se hace en cada service.
 */
@Component
@RequiredArgsConstructor
public class TenantIsolationInterceptor implements HandlerInterceptor {

    private final TenantContext tenantContext;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long clubId = tenantContext.restrictedClubId().orElse(null);
        if (clubId == null) return true; // ADMIN / VIEWER / sin autenticar: sin restricción acá

        @SuppressWarnings("unchecked")
        Map<String, String> vars =
                (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (vars == null || vars.isEmpty()) return true;

        String uri = request.getRequestURI();

        checkVar(vars, "tournamentId", clubId, "SELECT club_id FROM tournaments WHERE id = ?", "Torneo");
        checkVar(vars, "matchId", clubId,
                "SELECT t.club_id FROM matches m JOIN tournaments t ON t.id = m.tournament_id WHERE m.id = ?",
                "Partido");
        checkVar(vars, "zoneId", clubId,
                "SELECT t.club_id FROM zones z JOIN tournaments t ON t.id = z.tournament_id WHERE z.id = ?",
                "Zona");
        checkVar(vars, "complexId", clubId, "SELECT club_id FROM complexes WHERE id = ?", "Complejo");
        checkVar(vars, "courtId", clubId,
                "SELECT cx.club_id FROM courts c JOIN complexes cx ON cx.id = c.complex_id WHERE c.id = ?",
                "Cancha");

        // Controllers que usan {id} genérico: se desambigua por prefijo de ruta.
        if (vars.containsKey("id")) {
            if (uri.startsWith("/api/tournaments/")) {
                checkVar(vars, "id", clubId, "SELECT club_id FROM tournaments WHERE id = ?", "Torneo");
            } else if (uri.startsWith("/api/complexes/")) {
                checkVar(vars, "id", clubId, "SELECT club_id FROM complexes WHERE id = ?", "Complejo");
            } else if (uri.startsWith("/api/categories/")) {
                checkVar(vars, "id", clubId, "SELECT club_id FROM categories WHERE id = ?", "Categoría");
            }
        }
        return true;
    }

    private void checkVar(Map<String, String> vars, String varName, Long clubId, String sql, String entity) {
        String raw = vars.get(varName);
        if (raw == null) return;
        long id;
        try {
            id = Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return; // id no numérico: que lo resuelva el controller
        }
        var owners = jdbcTemplate.queryForList(sql, Long.class, id);
        if (owners.isEmpty()) return; // no existe: el service devuelve su propio 404
        Long ownerClubId = owners.getFirst();
        if (!clubId.equals(ownerClubId)) {
            throw new ResourceNotFoundException(entity, id);
        }
    }
}
