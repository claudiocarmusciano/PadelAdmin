package com.padeladmin.padeladmin.dto.settings;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GlobalSettingsDto {

    @Min(1)
    private int defaultMatchDurationMinutes;

    @Min(0)
    private int defaultMinIntervalMinutes;
}
