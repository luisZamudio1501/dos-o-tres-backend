package com.dosotres.chain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateChainRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @NotNull Integer slotMinutes,
        @NotNull @Min(0) @Max(1439) Integer dailyStartMinutes,
        @NotNull @Min(15) @Max(1440) Integer durationMinutes,
        @NotBlank String dateFrom,
        @NotBlank String dateTo
) {}
