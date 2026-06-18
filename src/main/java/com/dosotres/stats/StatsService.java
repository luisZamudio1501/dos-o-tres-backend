package com.dosotres.stats;

import com.dosotres.prayer.PrayerRequestStatus;
import com.dosotres.stats.StatsRepository.PrayedRequestView;
import com.dosotres.stats.dto.GroupStatsResponse;
import com.dosotres.stats.dto.MeStatsResponse;
import com.dosotres.stats.dto.MeStatsResponse.HeatmapDay;
import com.dosotres.stats.dto.MeStatsResponse.Milestone;
import com.dosotres.timer.PrayerSession;
import com.dosotres.timer.PrayerSession.SessionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agrega las métricas personales de progreso a partir de las sesiones de
 * oración del usuario. Toda la lógica día-local usa el {@link Clock} inyectable
 * (nunca {@code LocalDate.now()} suelto).
 */
@Service
public class StatsService {

    /** Duración mínima para que una sesión "cuente" como oración de ese día. */
    private static final int MIN_PRAYER_SECONDS = 60;
    private static final int HEATMAP_DAYS = 365;
    private static final int STREAK_7 = 7;
    private static final int STREAK_30 = 30;
    private static final int MILESTONE_MINUTES = 100;
    /** Ventana rodante para "miembros activos esta semana". */
    private static final int ACTIVE_WINDOW_DAYS = 7;

    private final StatsRepository statsRepository;
    private final Clock clock;

    public StatsService(StatsRepository statsRepository, Clock clock) {
        this.statsRepository = statsRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MeStatsResponse meStats(Long userId) {
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);

        List<PrayerSession> sessions =
                statsRepository.findByUserIdAndStatus(userId, SessionStatus.COMPLETED);

        // Minutos por día-local (para heatmap y minutos del mes/total).
        TreeMap<LocalDate, Long> secondsByDay = new TreeMap<>();
        // Días en los que hubo al menos una sesión de duración suficiente (para la racha).
        Set<LocalDate> activeDays = new TreeSet<>();
        long totalSeconds = 0;
        long monthSeconds = 0;
        YearMonth thisMonth = YearMonth.from(today);

        for (PrayerSession s : sessions) {
            LocalDate day = s.getStartedAt().atZone(zone).toLocalDate();
            int dur = s.getDurationSeconds();
            secondsByDay.merge(day, (long) dur, Long::sum);
            totalSeconds += dur;
            if (YearMonth.from(day).equals(thisMonth)) {
                monthSeconds += dur;
            }
            if (dur >= MIN_PRAYER_SECONDS) {
                activeDays.add(day);
            }
        }

        int currentStreak = currentStreak(activeDays, today);
        int longestStreak = longestStreak(activeDays);
        int totalMinutes = (int) (totalSeconds / 60);

        List<PrayedRequestView> prayed = statsRepository.findPrayedRequestsByUser(userId);
        int requestsPrayedFor = prayed.size();
        int requestsAnswered = (int) prayed.stream()
                .filter(p -> p.getStatus() == PrayerRequestStatus.ANSWERED)
                .count();

        List<HeatmapDay> heatmap = buildHeatmap(secondsByDay, today);
        List<Milestone> milestones = buildMilestones(longestStreak, totalMinutes);

        return new MeStatsResponse(
                currentStreak,
                longestStreak,
                (int) (monthSeconds / 60),
                totalMinutes,
                requestsPrayedFor,
                requestsAnswered,
                heatmap,
                milestones);
    }

    /**
     * Estadísticas agregadas del grupo. Todas las queries filtran por group_id,
     * de modo que la actividad personal (group_id NULL) y la de otros grupos
     * queda excluida por construcción.
     */
    @Transactional(readOnly = true)
    public GroupStatsResponse groupStats(Long groupId) {
        Instant since = clock.instant().minus(ACTIVE_WINDOW_DAYS, ChronoUnit.DAYS);
        long totalSeconds = statsRepository.sumGroupSeconds(groupId);
        long answered = statsRepository.countAnsweredByGroup(groupId);
        long activeMembers = statsRepository.countActiveMembersSince(groupId, since);
        return new GroupStatsResponse(
                (int) (totalSeconds / 60),
                (int) answered,
                (int) activeMembers);
    }

    /**
     * Días consecutivos terminando en hoy (o en ayer, si hoy aún no oró pero la
     * racha sigue viva). Si el último día activo es anterior a ayer, la racha es 0.
     */
    private int currentStreak(Set<LocalDate> activeDays, LocalDate today) {
        LocalDate cursor = activeDays.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (activeDays.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** Mejor racha histórica de días consecutivos. */
    private int longestStreak(Set<LocalDate> activeDays) {
        int longest = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate day : activeDays) {
            run = (prev != null && day.equals(prev.plusDays(1))) ? run + 1 : 1;
            longest = Math.max(longest, run);
            prev = day;
        }
        return longest;
    }

    private List<HeatmapDay> buildHeatmap(TreeMap<LocalDate, Long> secondsByDay, LocalDate today) {
        LocalDate from = today.minusDays(HEATMAP_DAYS - 1L);
        List<HeatmapDay> heatmap = new ArrayList<>();
        for (var entry : secondsByDay.tailMap(from).entrySet()) {
            int minutes = (int) (entry.getValue() / 60);
            if (minutes > 0) {
                heatmap.add(new HeatmapDay(entry.getKey().toString(), minutes));
            }
        }
        return heatmap;
    }

    private List<Milestone> buildMilestones(int longestStreak, int totalMinutes) {
        return List.of(
                new Milestone("STREAK_7", "7 días seguidos", longestStreak >= STREAK_7),
                new Milestone("STREAK_30", "30 días seguidos", longestStreak >= STREAK_30),
                new Milestone("MINUTES_100", "100 minutos de oración", totalMinutes >= MILESTONE_MINUTES));
    }
}
