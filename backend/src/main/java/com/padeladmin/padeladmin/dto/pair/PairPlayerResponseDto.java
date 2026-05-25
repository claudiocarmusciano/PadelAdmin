package com.padeladmin.padeladmin.dto.pair;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PairPlayerResponseDto {
    private Long id;
    private String firstName;
    private String lastName;
    private String phone;
    private Long categoryId;       // categoría elegida para sus puntos
    private String categoryName;   // nombre de esa categoría
    private Double points;         // puntos en esa categoría
}
