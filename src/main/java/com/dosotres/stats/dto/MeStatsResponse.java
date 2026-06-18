package com.dosotres.stats.dto;

import java.util.List;

/**
 * Estadísticas personales del usuario autenticado (cross-group + privados).
 * Todo se calcula por agregación; no hay tablas de estadística.
 */
public record MeStatsResponse(
        int currentStreak,
        int longestStreak,
        int minutesThisMonth,
        int totalMinutes,
        int requestsPrayedFor,
        int requestsAnswered,
        List<HeatmapDay> heatmap,
        List<Milestone> milestones
) {

    /** Un día con actividad en el heatmap de constancia (ventana 365 días). */
    public record HeatmapDay(String date, int minutes) {}

    /** Hito de gamificación suave; derivado al vuelo, sin ranking competitivo. */
    public record Milestone(String code, String label, boolean achieved) {}
}
