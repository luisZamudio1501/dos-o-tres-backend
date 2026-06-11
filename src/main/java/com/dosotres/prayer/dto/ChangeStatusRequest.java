package com.dosotres.prayer.dto;

import com.dosotres.prayer.PrayerRequestStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeStatusRequest(
        @NotNull PrayerRequestStatus status,
        @Size(max = 2000) String testimony
) {}
