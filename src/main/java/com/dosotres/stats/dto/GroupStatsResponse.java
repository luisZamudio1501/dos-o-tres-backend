package com.dosotres.stats.dto;

/**
 * Estadísticas agregadas de un grupo. Solo refleja actividad de ESTE grupo:
 * nunca incluye pedidos privados ni actividad de otros grupos.
 */
public record GroupStatsResponse(
        int totalMinutes,
        int answeredRequests,
        int activeMembersThisWeek
) {}
