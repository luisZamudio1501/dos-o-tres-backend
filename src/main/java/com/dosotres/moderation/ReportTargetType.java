package com.dosotres.moderation;

/** Tipo de contenido reportado (ADR-009). Polimórfico vía {@code targetId}. */
public enum ReportTargetType {
    PUBLIC_REQUEST,
    MESSAGE,
    USER
}
