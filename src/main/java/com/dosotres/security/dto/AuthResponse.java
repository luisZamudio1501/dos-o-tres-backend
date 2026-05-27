package com.dosotres.security.dto;

public record AuthResponse(
        Long id,
        String email,
        String displayName,
        String token
) {}
