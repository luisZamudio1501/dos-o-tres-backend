package com.dosotres.moderation.dto;

import jakarta.validation.constraints.NotNull;

/** Usuario a bloquear. */
public record BlockUserRequest(@NotNull Long userId) {}
