package com.padeladmin.padeladmin.dto.club;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Alta de club por el super-admin: datos del club + email del usuario CLUB que lo administrará. */
@Data
public class ClubRequestDto {
    @NotBlank(message = "El nombre del club es obligatorio")
    private String name;

    private String address;
    private String phone;

    @NotBlank(message = "El email del administrador del club es obligatorio")
    @Email(message = "Email inválido")
    private String adminEmail;
}
