package com.padeladmin.padeladmin.dto.player;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PlayerResponseDto {
    private Long id;
    private String firstName;
    private String lastName;
    private String phone;
    private String telegramChatId;
    private LocalDateTime createdAt;
}
