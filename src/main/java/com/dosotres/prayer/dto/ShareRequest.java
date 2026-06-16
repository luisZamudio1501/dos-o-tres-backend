package com.dosotres.prayer.dto;

import jakarta.validation.constraints.NotNull;

/** Cuerpo de POST /me/prayer-requests/{id}/share — compartir un pedido privado con un grupo. */
public record ShareRequest(@NotNull Long groupId) {}
