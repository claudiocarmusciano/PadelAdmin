package com.padeladmin.padeladmin.dto.settings;

import com.padeladmin.padeladmin.enums.TournamentStage;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointConfigDto {

    private TournamentStage stage;

    @Min(0)
    private int points;
}
