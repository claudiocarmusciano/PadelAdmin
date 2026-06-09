package com.padeladmin.padeladmin.service;

import com.padeladmin.padeladmin.config.GuestSeeder;
import com.padeladmin.padeladmin.dto.auth.AuthResponse;
import com.padeladmin.padeladmin.dto.auth.LoginRequest;
import com.padeladmin.padeladmin.dto.auth.RegisterRequest;
import com.padeladmin.padeladmin.entity.User;
import com.padeladmin.padeladmin.enums.UserRole;
import com.padeladmin.padeladmin.exception.BusinessException;
import com.padeladmin.padeladmin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("Ya existe una cuenta con ese email");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.VIEWER)
                .active(true)
                .build();

        userRepository.save(user);
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );
        } catch (BadCredentialsException ex) {
            throw new BusinessException("Email o contraseña incorrectos");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Email o contraseña incorrectos"));

        if (!user.isActive()) {
            throw new BusinessException("La cuenta está deshabilitada");
        }

        return buildAuthResponse(user);
    }

    /**
     * Cambio de contraseña del usuario autenticado. Usado también en el primer ingreso
     * (mustChangePassword): al completarse, el flag se apaga y se emite un token nuevo.
     */
    @Transactional
    public AuthResponse changePassword(String email, com.padeladmin.padeladmin.dto.auth.ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("La contraseña actual es incorrecta");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException("La contraseña nueva no puede ser igual a la actual");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    /** Acceso de invitado (solo lectura): emite un token VIEWER sin contraseña. */
    public AuthResponse loginAsGuest() {
        User guest = userRepository.findByEmail(GuestSeeder.GUEST_EMAIL)
                .orElseThrow(() -> new BusinessException("El acceso de invitado no está disponible"));
        if (!guest.isActive()) {
            throw new BusinessException("El acceso de invitado está deshabilitado");
        }
        return buildAuthResponse(guest);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtService.generateToken(user);
        Long clubId = user.getClub() != null ? user.getClub().getId() : null;
        return new AuthResponse(token, user.getEmail(), user.getRole(), jwtService.getExpiration(token),
                user.isMustChangePassword(), clubId);
    }
}
