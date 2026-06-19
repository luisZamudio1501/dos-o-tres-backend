package com.dosotres.user;

/**
 * Visibilidad del teléfono del usuario (M.1). Privado por defecto.
 * Nunca se expone en el muro público, sea cual sea el valor.
 */
public enum PhoneVisibility {
    /** Solo el propio usuario lo ve. */
    PRIVATE,
    /** Visible para los miembros de sus grupos. */
    GROUP
}
