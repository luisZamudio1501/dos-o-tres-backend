package com.dosotres.messaging.dto;

import jakarta.validation.constraints.NotNull;

/** Iniciar (o recuperar) la conversación 1:1 con otro usuario. */
public record StartConversationRequest(@NotNull Long userId) {}
