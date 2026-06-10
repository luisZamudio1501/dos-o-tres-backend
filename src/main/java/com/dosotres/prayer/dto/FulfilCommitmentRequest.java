package com.dosotres.prayer.dto;

import jakarta.validation.constraints.NotBlank;

public record FulfilCommitmentRequest(
        @NotBlank String sessionId
) {}
