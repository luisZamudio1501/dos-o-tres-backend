package com.dosotres.user;

/**
 * Rol global del usuario en la comunidad (ADR-006). Ortogonal al
 * {@code GroupRole} por-grupo: gobierna la moderación de contenido público.
 */
public enum GlobalRole {
    USER,
    MODERATOR
}
