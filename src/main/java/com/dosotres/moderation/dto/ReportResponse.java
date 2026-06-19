package com.dosotres.moderation.dto;

public record ReportResponse(
        Long id,
        Long reporterId,
        String reporterName,
        String targetType,
        Long targetId,
        String reason,
        String status,
        String createdAt,
        Long resolvedById,
        String resolvedAt
) {}
