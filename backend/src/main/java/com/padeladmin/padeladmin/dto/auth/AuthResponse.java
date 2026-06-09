package com.padeladmin.padeladmin.dto.auth;

import com.padeladmin.padeladmin.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String email;
    private UserRole role;
    private Instant expiresAt;
    private boolean mustChangePassword;  // true → forzar cambio de contraseña en el primer login
    private Long clubId;                  // club al que pertenece (rol CLUB), null si no aplica
}
