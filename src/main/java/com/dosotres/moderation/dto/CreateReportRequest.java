package com.dosotres.moderation.dto;

import com.dosotres.moderation.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotNull ReportTargetType targetType,
        @NotNull Long targetId,
        @Size(max = 500) String reason
) {}
