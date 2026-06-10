package com.dosotres.prayer.dto;

import jakarta.validation.constraints.NotNull;

public record CreateCommitmentRequest(
        @NotNull Long prayerRequestId,
        @NotNull String committedDate
) {}
