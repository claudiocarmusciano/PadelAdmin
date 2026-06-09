package com.padeladmin.padeladmin.dto.club;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ClubResponseDto {
    private Long id;
    private String name;
    private String address;
    private String phone;
    private boolean active;
    private LocalDateTime createdAt;
    private String adminEmail;          // email del usuario CLUB
    // Solo se devuelve al CREAR el club Y si NO se pudo enviar por email (fallback).
    private String generatedPassword;
    private boolean emailSent;          // true = la contraseña se mandó al email del club
}
