package com.dosotres.publicwall;

/**
 * Estado de moderación de un pedido público (M.2/M.3). HIDDEN lo oculta del
 * feed sin borrarlo. El moderador global lo gestiona en M.3.
 */
public enum ModerationStatus {
    VISIBLE,
    HIDDEN
}
