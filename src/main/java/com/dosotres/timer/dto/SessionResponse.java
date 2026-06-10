package com.dosotres.timer.dto;

public record SessionResponse(
        String id,
        Long userId,
        Long groupId,
        String startedAt,
        int durationSeconds,
        String status,
        String lastSyncAt
) {}
