package com.padeladmin.padeladmin.security;

import com.padeladmin.padeladmin.enums.UserRole;
import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Principal autenticado con el contexto multi-tenant: además del email/rol de Spring Security,
 * carga el clubId (rol CLUB) y el playerId (rol PLAYER) para que los servicios filtren por
 * tenant sin volver a consultar la base.
 */
@Getter
public class AppPrincipal extends org.springframework.security.core.userdetails.User {

    private final Long userId;
    private final UserRole role;
    private final Long clubId;
    private final Long playerId;

    public AppPrincipal(com.padeladmin.padeladmin.entity.User user) {
        super(user.getEmail(),
                user.getPasswordHash() == null ? "" : user.getPasswordHash(),
                user.isActive(), true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        this.userId = user.getId();
        this.role = user.getRole();
        this.clubId = user.getClub() != null ? user.getClub().getId() : null;
        this.playerId = user.getPlayer() != null ? user.getPlayer().getId() : null;
    }
}
