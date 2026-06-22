package com.dosotres.admin.dto;

import java.time.Instant;

/**
 * Fila de la vista CRM de usuarios (gateada ADMIN). Expone PII (email) — solo
 * accesible al dueño, validado server-side.
 */
public record AdminUserResponse(
        Long id,
        String email,
        String displayName,
        String country,
        String province,
        Integer age,
        String globalRole,
        Instant createdAt,
        Instant lastSeenAt
) {}
