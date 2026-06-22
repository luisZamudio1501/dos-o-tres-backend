package com.dosotres.publicwall.dto;

/** Quién oró por un pedido público, visto por el autor. userId null = anónimo. */
public record PrayerEntryResponse(
        Long userId,
        String displayName,
        String prayedAt
) {}
