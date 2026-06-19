package com.dosotres.prayer;

/**
 * Evento de dominio: alguien oró por un pedido. Solo se avisa al autor del
 * pedido (no a todo el grupo, para no generar ruido), salvo que el autor
 * haya orado por su propio pedido.
 * Lo consume el módulo de notificaciones (push) de forma asíncrona tras commit.
 */
public record PrayerPrayedEvent(Long requestId, String title, Long authorId, Long prayingUserId,
                                 String prayingUserName, boolean prayingPrivately) {}
