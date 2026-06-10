package com.dosotres.timer.dto;

import jakarta.validation.constraints.Min;

public record SyncSessionRequest(
        @Min(0) int durationSeconds
) {}
