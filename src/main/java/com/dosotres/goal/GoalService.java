package com.dosotres.goal;

import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.common.exception.ValidationException;
import com.dosotres.goal.dto.CreateGoalRequest;
import com.dosotres.goal.dto.GoalResponse;
import com.dosotres.goal.dto.ReminderStatusResponse;
import com.dosotres.goal.dto.UpdateGoalRequest;
import com.dosotres.stats.StatsRepository;
import com.dosotres.timer.PrayerSession;
import com.dosotres.timer.PrayerSession.SessionStatus;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Metas de oración (PrayerGoal). El cumplimiento y la racha se derivan de las
 * sesiones del cronómetro (sin tablas de cumplimiento), igual que las stats.
 */
@Service
@Transactional
public class GoalService {

    private static final String DEFAULT_TZ = "America/Argentina/Buenos_Aires";

    private final GoalRepository goalRepository;
    private final StatsRepository statsRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public GoalService(GoalRepository goalRepository,
                       StatsRepository statsRepository,
                       UserRepository userRepository,
                       Clock clock) {
        this.goalRepository = goalRepository;
        this.statsRepository = statsRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    public GoalResponse create(Long userId, CreateGoalRequest req) {
        validate(req.mode(), req.scheduledTime(), req.periodStart(), req.periodEnd());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        PrayerGoal goal = new PrayerGoal();
        goal.setOwnerUserId(userId);
        goal.setDailyMinutes(req.dailyMinutes());
        goal.setPeriodStart(req.periodStart());
        goal.setPeriodEnd(req.periodEnd());
        goal.setMode(req.mode());
        goal.setScheduledTime(req.mode() == GoalMode.SCHEDULED ? req.scheduledTime() : null);
        goal.setTimezone(user.getTimezone() != null ? user.getTimezone() : DEFAULT_TZ);
        goalRepository.save(goal);

        return toResponse(goal, sessionsOf(userId));
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> list(Long userId) {
        List<PrayerSession> sessions = sessionsOf(userId);
        return goalRepository.findByOwnerUserIdOrderByCreatedAtDesc(userId).stream()
                .map(goal -> toResponse(goal, sessions))
                .toList();
    }

    @Transactional(readOnly = true)
    public GoalResponse get(Long id, Long userId) {
        PrayerGoal goal = findOwned(id, userId);
        return toResponse(goal, sessionsOf(userId));
    }

    public GoalResponse update(Long id, Long userId, UpdateGoalRequest req) {
        PrayerGoal goal = findOwned(id, userId);
        if (req.dailyMinutes() != null) goal.setDailyMinutes(req.dailyMinutes());
        if (req.periodStart() != null) goal.setPeriodStart(req.periodStart());
        if (req.periodEnd() != null) goal.setPeriodEnd(req.periodEnd());
        if (req.mode() != null) goal.setMode(req.mode());
        if (req.scheduledTime() != null) goal.setScheduledTime(req.scheduledTime());

        validate(goal.getMode(), goal.getScheduledTime(), goal.getPeriodStart(), goal.getPeriodEnd());
        if (goal.getMode() == GoalMode.FREE) {
            goal.setScheduledTime(null);
        }
        goalRepository.save(goal);
        return toResponse(goal, sessionsOf(userId));
    }

    public void delete(Long id, Long userId) {
        goalRepository.delete(findOwned(id, userId));
    }

    /**
     * ¿Corresponde mandar el recordatorio de esta meta ahora? Solo metas
     * SCHEDULED activas hoy, cuya hora ya pasó, que no se recordaron hoy y que
     * aún no se cumplieron. Encapsula toda la decisión (zona, período, hora,
     * idempotencia y cumplimiento) para que el job sea delgado.
     */
    @Transactional(readOnly = true)
    public boolean isReminderDue(PrayerGoal goal) {
        if (goal.getMode() != GoalMode.SCHEDULED || goal.getScheduledTime() == null) {
            return false;
        }
        ZoneId zone = zoneOf(goal);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        if (today.isBefore(goal.getPeriodStart()) || today.isAfter(goal.getPeriodEnd())) {
            return false;
        }
        if (today.equals(goal.getLastRemindedOn())) {
            return false;
        }
        if (LocalTime.ofInstant(now, zone).isBefore(goal.getScheduledTime())) {
            return false;
        }
        return !hasMetToday(goal, zone, today);
    }

    /** Marca la meta como recordada hoy (idempotencia diaria del recordatorio). */
    public void markRemindedToday(PrayerGoal goal) {
        goal.setLastRemindedOn(LocalDate.ofInstant(clock.instant(), zoneOf(goal)));
        goalRepository.save(goal);
    }

    /**
     * Diagnóstico legible de si esta meta avisará hoy y por qué (no). Misma
     * decisión que {@link #isReminderDue}, pero explicada para soporte/UI.
     */
    @Transactional(readOnly = true)
    public ReminderStatusResponse reminderStatus(Long id, Long userId) {
        PrayerGoal goal = findOwned(id, userId);
        String scheduledTime = goal.getScheduledTime() != null ? goal.getScheduledTime().toString() : null;

        if (goal.getMode() == GoalMode.FREE) {
            return new ReminderStatusResponse(goal.getMode().name(), null, false,
                    "Modo libre: esta meta no envía recordatorios.");
        }

        ZoneId zone = zoneOf(goal);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);

        if (today.isBefore(goal.getPeriodStart()) || today.isAfter(goal.getPeriodEnd())) {
            return new ReminderStatusResponse(goal.getMode().name(), scheduledTime, false,
                    "La meta no está activa hoy (fuera del período).");
        }
        if (today.equals(goal.getLastRemindedOn())) {
            return new ReminderStatusResponse(goal.getMode().name(), scheduledTime, false,
                    "Ya se envió el recordatorio de hoy.");
        }
        if (LocalTime.ofInstant(now, zone).isBefore(goal.getScheduledTime())) {
            return new ReminderStatusResponse(goal.getMode().name(), scheduledTime, true,
                    "Te avisaremos hoy a las " + scheduledTime + ".");
        }
        if (hasMetToday(goal, zone, today)) {
            return new ReminderStatusResponse(goal.getMode().name(), scheduledTime, false,
                    "Ya cumpliste la meta de hoy: no se enviará recordatorio.");
        }
        return new ReminderStatusResponse(goal.getMode().name(), scheduledTime, true,
                "Se enviará el recordatorio en la próxima corrida (cada pocos minutos).");
    }

    // ── internos ──

    private boolean hasMetToday(PrayerGoal goal, ZoneId zone, LocalDate today) {
        long todaySeconds = sessionsOf(goal.getOwnerUserId()).stream()
                .filter(s -> s.getStartedAt().atZone(zone).toLocalDate().equals(today))
                .mapToLong(PrayerSession::getDurationSeconds)
                .sum();
        return todaySeconds >= goal.getDailyMinutes() * 60L;
    }

    private void validate(GoalMode mode, java.time.LocalTime scheduledTime,
                          LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new ValidationException("El fin del período no puede ser anterior al inicio");
        }
        if (mode == GoalMode.SCHEDULED && scheduledTime == null) {
            throw new ValidationException("Una meta con horario necesita una hora");
        }
    }

    private PrayerGoal findOwned(Long id, Long userId) {
        PrayerGoal goal = goalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PrayerGoal", "id", id));
        if (!goal.getOwnerUserId().equals(userId)) {
            throw new ForbiddenException("Esta meta no es tuya");
        }
        return goal;
    }

    private List<PrayerSession> sessionsOf(Long userId) {
        return statsRepository.findByUserIdAndStatus(userId, SessionStatus.COMPLETED);
    }

    private GoalResponse toResponse(PrayerGoal goal, List<PrayerSession> sessions) {
        ZoneId zone = zoneOf(goal);
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);

        Map<LocalDate, Long> secondsByDay = new HashMap<>();
        for (PrayerSession s : sessions) {
            LocalDate day = s.getStartedAt().atZone(zone).toLocalDate();
            secondsByDay.merge(day, (long) s.getDurationSeconds(), Long::sum);
        }

        long threshold = goal.getDailyMinutes() * 60L;
        long todaySeconds = secondsByDay.getOrDefault(today, 0L);
        boolean metToday = todaySeconds >= threshold;
        int currentStreak = currentStreak(goal, secondsByDay, today, threshold);
        boolean active = !today.isBefore(goal.getPeriodStart()) && !today.isAfter(goal.getPeriodEnd());

        return new GoalResponse(
                goal.getId(),
                goal.getDailyMinutes(),
                (int) (todaySeconds / 60),
                metToday,
                currentStreak,
                goal.getMode().name(),
                goal.getScheduledTime() != null ? goal.getScheduledTime().toString() : null,
                goal.getPeriodStart().toString(),
                goal.getPeriodEnd().toString(),
                active);
    }

    /**
     * Días consecutivos cumpliendo la meta hasta hoy (o ayer, si hoy aún no la
     * cumplió pero la racha sigue viva), sin retroceder más allá del inicio del período.
     */
    private int currentStreak(PrayerGoal goal, Map<LocalDate, Long> secondsByDay,
                              LocalDate today, long threshold) {
        LocalDate cursor = secondsByDay.getOrDefault(today, 0L) >= threshold
                ? today
                : today.minusDays(1);
        int streak = 0;
        while (!cursor.isBefore(goal.getPeriodStart())
                && secondsByDay.getOrDefault(cursor, 0L) >= threshold) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private ZoneId zoneOf(PrayerGoal goal) {
        try {
            return ZoneId.of(goal.getTimezone());
        } catch (Exception e) {
            return ZoneOffset.UTC;
        }
    }
}
