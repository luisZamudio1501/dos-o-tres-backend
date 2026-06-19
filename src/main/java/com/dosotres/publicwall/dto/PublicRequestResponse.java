package com.dosotres.publicwall.dto;

public record PublicRequestResponse(
        Long id,
        Long authorId,        // null si es anónimo
        String authorName,    // null si es anónimo
        String authorCountry, // null si es anónimo
        boolean anonymous,
        String title,
        String body,
        String status,
        int prayCount,
        boolean iPrayed,
        boolean mine,
        String createdAt
) {}
