package com.dosotres.publicwall.dto;

import com.dosotres.publicwall.ModerationStatus;
import jakarta.validation.constraints.NotNull;

/** El moderador global cambia la visibilidad de un pedido público (VISIBLE/HIDDEN). */
public record UpdateVisibilityRequest(@NotNull ModerationStatus moderationStatus) {}
