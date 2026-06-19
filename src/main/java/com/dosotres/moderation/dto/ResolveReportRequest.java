package com.dosotres.moderation.dto;

import com.dosotres.moderation.ReportStatus;
import jakarta.validation.constraints.NotNull;

/** El moderador resuelve un reporte como RESOLVED o DISMISSED. */
public record ResolveReportRequest(@NotNull ReportStatus status) {}
