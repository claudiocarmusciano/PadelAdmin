package com.padeladmin.padeladmin.dto.player;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PlayerRequestDto {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String firstName;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 100, message = "El apellido no puede superar los 100 caracteres")
    private String lastName;

    @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres")
    private String phone;

    @Size(max = 50, message = "El Telegram ID no puede superar los 50 caracteres")
    private String telegramChatId;
}
