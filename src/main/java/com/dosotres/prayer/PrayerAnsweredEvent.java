package com.dosotres.prayer;

/**
 * Evento de dominio: un pedido de oración fue marcado como respondido.
 * Lo consume el módulo de notificaciones (push) de forma asíncrona tras commit.
 */
public record PrayerAnsweredEvent(Long requestId, String title, Long authorId, Long groupId) {}
