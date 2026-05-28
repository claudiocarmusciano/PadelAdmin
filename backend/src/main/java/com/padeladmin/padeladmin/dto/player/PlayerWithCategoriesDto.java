package com.padeladmin.padeladmin.dto.player;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Jugador con todas sus categorías (incluyendo posición en el ranking de cada una). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerWithCategoriesDto {
    private Long id;
    private String firstName;
    private String lastName;
    private String phone;
    private String telegramChatId;
    private List<PlayerCategoryRankDto> categories;
}
