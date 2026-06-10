package com.dosotres.prayer.dto;

public record CommitmentResponse(
        Long id,
        Long prayerRequestId,
        String prayerRequestTitle,
        Long userId,
        String userName,
        String committedDate,
        boolean fulfilled,
        String fulfilledAt,
        String sessionId
) {}
