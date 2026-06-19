package com.dosotres.chain.dto;

public record ChainResponse(
        Long id,
        String name,
        String description,
        int slotMinutes,
        int dailyStartMinutes,
        int durationMinutes,
        String dateFrom,
        String dateTo,
        String status,
        int totalSlots,
        int coveredSlots,
        Long createdById,
        String createdByName
) {}
