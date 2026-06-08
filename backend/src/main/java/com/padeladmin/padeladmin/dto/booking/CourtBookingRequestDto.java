package com.padeladmin.padeladmin.dto.booking;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourtBookingRequestDto {

    @NotNull(message = "El ID de la cancha es requerido")
    private Long courtId;

    @NotNull(message = "La fecha de la reserva es requerida")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate bookingDate;

    @NotNull(message = "La hora de inicio es requerida")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @NotBlank(message = "El nombre del cliente es requerido")
    @Size(min = 2, max = 255, message = "El nombre debe tener entre 2 y 255 caracteres")
    private String customerName;

    @NotBlank(message = "El teléfono es requerido")
    @Size(min = 7, max = 20, message = "El teléfono debe tener entre 7 y 20 caracteres")
    private String customerPhone;

    @Size(max = 500, message = "Las notas no pueden superar los 500 caracteres")
    private String notes;
}
