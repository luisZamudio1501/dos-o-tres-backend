package com.dosotres.timer.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * El grupo de la sesión NO viaja en el body: se toma siempre del header
 * X-Group-Id ya validado por GroupContextFilter (fix 3.1 — auditoría
 * 2026-06-12: un groupId en el body permitía saltarse la membresía).
 */
public record StartSessionRequest(
        @NotBlank String id,
        List<Long> prayerRequestIds,
        Boolean isPrivate
) {}
