package com.dosotres.prayer.dto;

/**
 * Una entrada del historial "quién oró" por un pedido.
 * El nombre ya viene enmascarado como "Alguien" cuando la oración fue privada.
 */
public record PrayerLogResponse(
        String displayName,
        String date,
        boolean isPrivate
) {}
