package com.padeladmin.padeladmin.dto.category;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryResponseDto {
    private Long id;
    private String name;
    private String description;
    /** Club dueño de la categoría (null = global, anterior al multi-tenant). */
    private String clubName;
}
