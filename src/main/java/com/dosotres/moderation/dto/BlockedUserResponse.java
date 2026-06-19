package com.dosotres.moderation.dto;

public record BlockedUserResponse(
        Long userId,
        String displayName
) {}
