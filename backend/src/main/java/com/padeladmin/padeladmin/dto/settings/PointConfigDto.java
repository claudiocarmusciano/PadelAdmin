package com.padeladmin.padeladmin.dto.settings;

import com.padeladmin.padeladmin.enums.TournamentStage;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointConfigDto {

    private TournamentStage stage;

    @DecimalMin("0.0")
    private BigDecimal points;
}
