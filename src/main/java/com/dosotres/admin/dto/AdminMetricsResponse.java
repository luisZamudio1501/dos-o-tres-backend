package com.dosotres.admin.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Snapshot de métricas de crecimiento del panel de administración (Fase 1).
 * Totales actuales + tendencias diarias + actividad + demografía.
 */
public record AdminMetricsResponse(
        Totals totals,
        Active active,
        Trends trends,
        List<NameCount> byCountry,
        List<NameCount> byProvince,
        List<NameCount> byAge
) {

    /** Totales acumulados a hoy. */
    public record Totals(
            long users,
            long groups,
            double avgMembersPerGroup,
            long requestsActive,
            long requestsOnHold,
            long requestsAnswered,
            long testimonies,
            long sessions,
            long prayedMinutes,
            long goalsTotal,
            long goalsActive,
            long topics,
            long publicRequests,
            long publicPrayers,
            long publicTestimonies,
            long messages,
            long conversations,
            long linksPending,
            long linksAccepted,
            long reportsOpen,
            long blocks,
            long usersWithPush,
            double pushPercent,
            long usersWithProfile,
            double profilePercent,
            long usersWithAge,
            double agePercent
    ) {}

    /** Usuarios activos: oraron (sesiones) y abrieron la app (last_seen_at). */
    public record Active(long prayed7d, long prayed30d, long opened7d, long opened30d) {}

    /** Series diarias para los últimos {@code days} días (huecos rellenados con 0). */
    public record Trends(
            int days,
            List<DailyCount> newUsers,
            List<DailyCount> newGroups,
            List<DailyCount> newRequests,
            List<DailyCount> sessions,
            List<DailyCount> wallActivity,
            List<DailyCount> messages
    ) {}

    public record DailyCount(LocalDate date, long count) {}

    public record NameCount(String name, long count) {}
}
