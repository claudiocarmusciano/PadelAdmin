package com.padeladmin.padeladmin.dto.complex;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CourtRequestDto {

    @NotBlank(message = "El nombre de la cancha es obligatorio")
    @Size(max = 50, message = "El nombre no puede superar los 50 caracteres")
    private String name;
}
