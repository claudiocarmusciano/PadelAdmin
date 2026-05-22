package com.padeladmin.padeladmin.dto.complex;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ComplexRequestDto {

    @NotBlank(message = "El nombre del complejo es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
    private String name;

    @Size(max = 255, message = "La dirección no puede superar los 255 caracteres")
    private String address;

    @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres")
    private String phone;
}
