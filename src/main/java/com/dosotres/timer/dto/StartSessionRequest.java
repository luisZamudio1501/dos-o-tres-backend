package com.dosotres.timer.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record StartSessionRequest(
        @NotBlank String id,
        Long groupId,
        List<Long> prayerRequestIds,
        Boolean isPrivate
) {}
