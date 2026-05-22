package com.padeladmin.padeladmin.dto.complex;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ComplexResponseDto {
    private Long id;
    private String name;
    private String address;
    private String phone;
    private List<CourtResponseDto> courts;
}
