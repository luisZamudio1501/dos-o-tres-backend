package com.dosotres.timer.dto;

import jakarta.validation.constraints.NotBlank;

public record StartSessionRequest(
        @NotBlank String id,
        Long groupId
) {}
