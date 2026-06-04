package com.padeladmin.padeladmin.controller;

import com.padeladmin.padeladmin.dto.auth.AuthResponse;
import com.padeladmin.padeladmin.dto.auth.LoginRequest;
import com.padeladmin.padeladmin.dto.auth.RegisterRequest;
import com.padeladmin.padeladmin.entity.User;
import com.padeladmin.padeladmin.repository.UserRepository;
import com.padeladmin.padeladmin.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Acceso de invitado de solo lectura (rol VIEWER), sin credenciales. */
    @PostMapping("/guest")
    public ResponseEntity<AuthResponse> guest() {
        return ResponseEntity.ok(authService.loginAsGuest());
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        String email = auth.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return ResponseEntity.ok(Map.of(
                "email", user.getEmail(),
                "role", user.getRole(),
                "active", user.isActive()
        ));
    }
}
