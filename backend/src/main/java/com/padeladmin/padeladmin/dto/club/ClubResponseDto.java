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
    // Solo se devuelve al CREAR el club (la contraseña generada para pasarle al club).
    // En el listado va null.
    private String generatedPassword;
}
