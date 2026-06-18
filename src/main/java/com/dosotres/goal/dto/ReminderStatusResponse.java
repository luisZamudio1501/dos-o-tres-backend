package com.dosotres.goal.dto;

/**
 * Diagnóstico de si una meta recordará hoy y por qué (no). Soporte + feedback
 * de UI: ver F.1 (HITO-FIDELIZACION.md).
 */
public record ReminderStatusResponse(
        String mode,
        String scheduledTime,
        boolean willRemind,
        String reason
) {}
