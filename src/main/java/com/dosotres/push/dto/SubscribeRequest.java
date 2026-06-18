package com.dosotres.push.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Suscripción enviada por el navegador (formato de PushSubscription.toJSON()):
 * { endpoint, keys: { p256dh, auth } }.
 */
public record SubscribeRequest(
        @NotBlank String endpoint,
        @NotNull @Valid Keys keys
) {
    public record Keys(
            @NotBlank String p256dh,
            @NotBlank String auth
    ) {}
}
