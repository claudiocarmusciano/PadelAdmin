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
}
