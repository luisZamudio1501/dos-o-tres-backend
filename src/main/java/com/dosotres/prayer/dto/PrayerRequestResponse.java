package com.dosotres.prayer.dto;

public record PrayerRequestResponse(
        Long id,
        Long authorId,
        String authorName,
        String title,
        String description,
        String status,
        String answeredAt,
        String testimony,
        String createdAt,
        int commitmentCount,
        int prayedByCount
) {}
