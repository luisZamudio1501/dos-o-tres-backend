package com.dosotres.prayer;

/**
 * Evento de dominio: se creó un pedido de oración en un grupo.
 * Lo consume el módulo de notificaciones (push) de forma asíncrona tras commit.
 */
public record PrayerRequestCreatedEvent(Long requestId, String title, Long authorId, Long groupId) {}
