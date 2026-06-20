package com.dosotres.messaging;

/**
 * Estado de una conversación (ADR-008). En Fase A (grupos en común) se crea
 * ACCEPTED. PENDING queda preparado para desconocidos (Fase B): el 1er mensaje
 * espera aceptación del receptor — sin migración nueva.
 */
public enum ConversationState {
    PENDING,
    ACCEPTED
}
