package com.padeladmin.padeladmin.dto.match;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class MatchResultRequestDto {

    @NotEmpty(message = "Debe ingresar al menos los sets jugados")
    @Size(min = 2, max = 3, message = "Un partido tiene entre 2 y 3 sets")
    @Valid
    private List<SetScoreDto> sets;
}
