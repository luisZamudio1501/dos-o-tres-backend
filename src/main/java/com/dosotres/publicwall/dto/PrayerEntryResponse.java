package com.dosotres.publicwall.dto;

/**
 * Quién oró por un pedido público, visto por el autor. userId null = anónimo.
 * prayerId es el id opaco de la oración: permite que el autor inicie un vínculo
 * (incluso con anónimos) sin exponer la identidad del orante.
 */
public record PrayerEntryResponse(
        Long prayerId,
        Long userId,
        String displayName,
        String prayedAt
) {}
