package com.dosotres.publicwall.dto;

public record PublicRequestResponse(
        Long id,
        Long authorId,        // null si es anónimo
        String authorName,    // null si es anónimo
        String authorCountry, // null si es anónimo
        boolean anonymous,
        String title,
        String body,
        String testimony,     // null salvo respondido con testimonio
        String status,
        int prayCount,
        boolean iPrayed,
        boolean mine,
        boolean archived,
        String createdAt,
        String answeredAt     // null salvo respondido
) {}
