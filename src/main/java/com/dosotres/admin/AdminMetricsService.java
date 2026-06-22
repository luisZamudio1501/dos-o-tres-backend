package com.dosotres.admin;

import com.dosotres.admin.dto.AdminMetricsResponse;
import com.dosotres.admin.dto.AdminMetricsResponse.Active;
import com.dosotres.admin.dto.AdminMetricsResponse.DailyCount;
import com.dosotres.admin.dto.AdminMetricsResponse.NameCount;
import com.dosotres.admin.dto.AdminMetricsResponse.Totals;
import com.dosotres.admin.dto.AdminMetricsResponse.Trends;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Métricas de crecimiento del panel de administración (Plan Panel de Administración,
 * Fase 1). Servicio de solo lectura: agrega con SQL nativo sobre las tablas
 * existentes (casi todo sale de {@code created_at} sin tabla de snapshots).
 * Gateado ADMIN.
 */
@Service
@Transactional(readOnly = true)
public class AdminMetricsService {

    private static final int MIN_DAYS = 7;
    private static final int MAX_DAYS = 365;

    @PersistenceContext
    private EntityManager em;

    private final AdminAccess adminAccess;
    private final Clock clock;

    public AdminMetricsService(AdminAccess adminAccess, Clock clock) {
        this.adminAccess = adminAccess;
        this.clock = clock;
    }

    public AdminMetricsResponse getMetrics(Long adminId, int requestedDays) {
        adminAccess.requireAdmin(adminId);

        int days = Math.max(MIN_DAYS, Math.min(MAX_DAYS, requestedDays));
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        Timestamp t7 = Timestamp.from(now.minus(Duration.ofDays(7)));
        Timestamp t30 = Timestamp.from(now.minus(Duration.ofDays(30)));
        LocalDate windowStart = today.minusDays(days - 1L);
        Timestamp since = Timestamp.from(windowStart.atStartOfDay(zone).toInstant());

        return new AdminMetricsResponse(
                buildTotals(today),
                buildActive(t7, t30),
                buildTrends(days, windowStart, today, since),
                groupByName("SELECT country, COUNT(*) FROM users GROUP BY country ORDER BY COUNT(*) DESC"),
                groupByName("SELECT province, COUNT(*) FROM users WHERE province IS NOT NULL "
                        + "GROUP BY province ORDER BY COUNT(*) DESC"),
                buildAgeBuckets()
        );
    }

    // ── Totales ───────────────────────────────────────────────────────────────

    private Totals buildTotals(LocalDate today) {
        long users = count("SELECT COUNT(*) FROM users");
        long groups = count("SELECT COUNT(*) FROM `groups`");
        long members = count("SELECT COUNT(*) FROM group_members");
        double avgMembers = groups == 0 ? 0.0 : (double) members / groups;

        Map<String, Long> byStatus = statusCounts(
                "SELECT status, COUNT(*) FROM prayer_requests GROUP BY status");

        long usersWithPush = count("SELECT COUNT(DISTINCT user_id) FROM push_subscriptions");
        long usersWithProfile = count("SELECT COUNT(*) FROM users WHERE country IS NOT NULL");
        long usersWithAge = count("SELECT COUNT(*) FROM users WHERE date_of_birth IS NOT NULL");

        return new Totals(
                users,
                groups,
                round1(avgMembers),
                byStatus.getOrDefault("ACTIVE", 0L),
                byStatus.getOrDefault("ON_HOLD", 0L),
                byStatus.getOrDefault("ANSWERED", 0L),
                count("SELECT COUNT(*) FROM prayer_requests WHERE testimony IS NOT NULL AND testimony <> ''"),
                count("SELECT COUNT(*) FROM prayer_sessions"),
                count("SELECT COALESCE(SUM(duration_seconds), 0) DIV 60 FROM prayer_sessions"),
                count("SELECT COUNT(*) FROM prayer_goals"),
                countParam("SELECT COUNT(*) FROM prayer_goals WHERE period_start <= ? AND period_end >= ?",
                        today, today),
                count("SELECT COUNT(*) FROM prayer_topics"),
                count("SELECT COUNT(*) FROM public_prayer_requests"),
                count("SELECT COUNT(*) FROM public_prayers"),
                count("SELECT COUNT(*) FROM public_prayer_requests WHERE testimony IS NOT NULL AND testimony <> ''"),
                count("SELECT COUNT(*) FROM messages"),
                count("SELECT COUNT(*) FROM conversations"),
                count("SELECT COUNT(*) FROM conversations WHERE state = 'PENDING'"),
                count("SELECT COUNT(*) FROM conversations WHERE state = 'ACCEPTED'"),
                count("SELECT COUNT(*) FROM reports WHERE status = 'OPEN'"),
                count("SELECT COUNT(*) FROM user_blocks"),
                usersWithPush,
                percent(usersWithPush, users),
                usersWithProfile,
                percent(usersWithProfile, users),
                usersWithAge,
                percent(usersWithAge, users)
        );
    }

    // ── Activos ────────────────────────────────────────────────────────────────

    private Active buildActive(Timestamp t7, Timestamp t30) {
        return new Active(
                countParam("SELECT COUNT(DISTINCT user_id) FROM prayer_sessions WHERE started_at >= ?", t7),
                countParam("SELECT COUNT(DISTINCT user_id) FROM prayer_sessions WHERE started_at >= ?", t30),
                countParam("SELECT COUNT(*) FROM users WHERE last_seen_at >= ?", t7),
                countParam("SELECT COUNT(*) FROM users WHERE last_seen_at >= ?", t30)
        );
    }

    // ── Tendencias ───────────────────────────────────────────────────────────────

    private Trends buildTrends(int days, LocalDate start, LocalDate end, Timestamp since) {
        return new Trends(
                days,
                daily("SELECT DATE(created_at), COUNT(*) FROM users WHERE created_at >= ? "
                        + "GROUP BY DATE(created_at)", since, start, end),
                daily("SELECT DATE(created_at), COUNT(*) FROM `groups` WHERE created_at >= ? "
                        + "GROUP BY DATE(created_at)", since, start, end),
                daily("SELECT DATE(created_at), COUNT(*) FROM prayer_requests WHERE created_at >= ? "
                        + "GROUP BY DATE(created_at)", since, start, end),
                daily("SELECT DATE(started_at), COUNT(*) FROM prayer_sessions WHERE started_at >= ? "
                        + "GROUP BY DATE(started_at)", since, start, end),
                daily("SELECT DATE(ts), COUNT(*) FROM ("
                        + "  SELECT created_at ts FROM public_prayer_requests WHERE created_at >= ?"
                        + "  UNION ALL"
                        + "  SELECT prayed_at ts FROM public_prayers WHERE prayed_at >= ?"
                        + ") x GROUP BY DATE(ts)", since, start, end, since),
                daily("SELECT DATE(created_at), COUNT(*) FROM messages WHERE created_at >= ? "
                        + "GROUP BY DATE(created_at)", since, start, end)
        );
    }

    // ── Demografía ───────────────────────────────────────────────────────────────

    private List<NameCount> buildAgeBuckets() {
        return groupByName(
                "SELECT bucket, COUNT(*) FROM ("
                        + "  SELECT CASE"
                        + "    WHEN date_of_birth IS NULL THEN 'unknown'"
                        + "    WHEN TIMESTAMPDIFF(YEAR, date_of_birth, CURDATE()) < 25 THEN '18-24'"
                        + "    WHEN TIMESTAMPDIFF(YEAR, date_of_birth, CURDATE()) < 35 THEN '25-34'"
                        + "    WHEN TIMESTAMPDIFF(YEAR, date_of_birth, CURDATE()) < 45 THEN '35-44'"
                        + "    WHEN TIMESTAMPDIFF(YEAR, date_of_birth, CURDATE()) < 55 THEN '45-54'"
                        + "    WHEN TIMESTAMPDIFF(YEAR, date_of_birth, CURDATE()) < 65 THEN '55-64'"
                        + "    ELSE '65+'"
                        + "  END bucket"
                        + "  FROM users"
                        + ") t GROUP BY bucket ORDER BY bucket");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private long count(String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }

    private long countParam(String sql, Object... params) {
        Query q = em.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            q.setParameter(i + 1, params[i]);
        }
        return ((Number) q.getSingleResult()).longValue();
    }

    private Map<String, Long> statusCounts(String sql) {
        Map<String, Long> out = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql).getResultList();
        for (Object[] row : rows) {
            out.put((String) row[0], ((Number) row[1]).longValue());
        }
        return out;
    }

    private List<NameCount> groupByName(String sql) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql).getResultList();
        List<NameCount> out = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            out.add(new NameCount(row[0] == null ? null : row[0].toString(),
                    ((Number) row[1]).longValue()));
        }
        return out;
    }

    /** Ejecuta la query de conteo diario y rellena con 0 los días sin datos. */
    private List<DailyCount> daily(String sql, Timestamp since, LocalDate start, LocalDate end,
                                   Object... extraParams) {
        Query q = em.createNativeQuery(sql);
        q.setParameter(1, since);
        for (int i = 0; i < extraParams.length; i++) {
            q.setParameter(i + 2, extraParams[i]);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();

        Map<LocalDate, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put(toLocalDate(row[0]), ((Number) row[1]).longValue());
        }

        List<DailyCount> series = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            series.add(new DailyCount(d, counts.getOrDefault(d, 0L)));
        }
        return series;
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return LocalDate.parse(value.toString());
    }

    private static double percent(long part, long total) {
        return total == 0 ? 0.0 : round1(part * 100.0 / total);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
