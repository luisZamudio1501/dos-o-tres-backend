package com.dosotres.activity.dto;

import java.util.Map;

public record ActivityEventResponse(
        Long id,
        String type,
        Long actorId,
        String actorName,
        Map<String, Object> payload,
        String createdAt
) {}
