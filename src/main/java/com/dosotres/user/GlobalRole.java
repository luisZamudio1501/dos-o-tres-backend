package com.dosotres.user;

/**
 * Rol global del usuario en la comunidad (ADR-006). Ortogonal al
 * {@code GroupRole} por-grupo: gobierna la moderación de contenido público.
 *
 * <p>{@code ADMIN} es superconjunto de {@code MODERATOR}: el dueño de la app
 * (panel de administración) puede además moderar contenido público.
 */
public enum GlobalRole {
    USER,
    MODERATOR,
    ADMIN;

    /** True si el rol puede moderar contenido público (MODERATOR o ADMIN). */
    public boolean canModerate() {
        return this == MODERATOR || this == ADMIN;
    }

    /** True si el rol tiene acceso al panel de administración (solo ADMIN). */
    public boolean isAdmin() {
        return this == ADMIN;
    }
}
