package com.dosotres.prayer.dto;

public record PrayerRequestResponse(
        Long id,
        Long authorId,
        String authorName,
        String title,
        String description,
        String status,
        String answeredAt,
        String createdAt,
        int commitmentCount
) {}
