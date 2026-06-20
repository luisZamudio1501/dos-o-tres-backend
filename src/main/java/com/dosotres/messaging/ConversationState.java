package com.dosotres.messaging;

/**
 * Estado de una conversación (ADR-008). Fase A (grupos en común): se crea
 * ACCEPTED directo. Fase B (desconocidos, opt-in): se crea PENDING y el
 * receptor decide ACCEPTED o DECLINED — sin migración nueva.
 */
public enum ConversationState {
    PENDING,
    ACCEPTED,
    DECLINED
}
